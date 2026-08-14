package com.example.gestioncommerciale.service.impl;

import com.example.gestioncommerciale.dto.PaiementRequest;
import com.example.gestioncommerciale.dto.PaiementResponse;
import com.example.gestioncommerciale.entity.Facture;
import com.example.gestioncommerciale.entity.NiveauNotification;
import com.example.gestioncommerciale.entity.TypeDocument;
import com.example.gestioncommerciale.entity.TypeNotification;
import com.example.gestioncommerciale.entity.Paiement;
import com.example.gestioncommerciale.entity.StatutFacture;
import com.example.gestioncommerciale.entity.StatutPaiement;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.FactureRepository;
import com.example.gestioncommerciale.repository.PaiementRepository;
import com.example.gestioncommerciale.service.ClientService;
import com.example.gestioncommerciale.service.NotificationService;
import com.example.gestioncommerciale.service.PaiementService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Encaissement des factures.
 *
 * <p>La regle qui gouverne tout : <strong>seul un paiement encaisse solde une
 * facture</strong>. Especes, carte et virement le sont d'emblee ; un cheque ou
 * une traite traversent d'abord un cycle, et l'argent n'arrive qu'au bout.
 * Compter un effet des sa reception ferait afficher "payee" a une facture qui ne
 * l'est pas, et libererait le plafond de credit du client sur un cheque qui peut
 * revenir impaye.
 *
 * <p>Le montant paye n'est jamais incremente : il est <em>recalcule</em> depuis
 * les paiements encaisses a chaque mouvement. Un rejet, un encaissement tardif
 * ou une suppression retombent ainsi juste sans arithmetique a rebours.
 */
@Service
@Transactional
public class PaiementServiceImpl implements PaiementService {

    /** Etats ou l'effet n'a pas encore livre son argent. */
    private static final Set<StatutPaiement> EN_ATTENTE =
            Set.of(StatutPaiement.RECU, StatutPaiement.DEPOSE);

    private final PaiementRepository paiementRepository;
    private final FactureRepository factureRepository;
    private final ClientService clientService;
    private final NotificationService notifications;

    public PaiementServiceImpl(PaiementRepository paiementRepository,
                               FactureRepository factureRepository,
                               ClientService clientService,
                               NotificationService notifications) {
        this.paiementRepository = paiementRepository;
        this.factureRepository = factureRepository;
        this.clientService = clientService;
        this.notifications = notifications;
    }

    @Override
    public PaiementResponse enregistrer(PaiementRequest request) {
        Facture facture = factureRepository.findById(request.factureId())
                .orElseThrow(() -> new ResourceNotFoundException("Facture", request.factureId()));

        if (facture.getStatut() == StatutFacture.ANNULEE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de payer une facture annulee");
        }

        Paiement paiement = Paiement.builder()
                .facture(facture)
                .montant(request.montant())
                .modePaiement(request.modePaiement())
                .reference(request.reference())
                .build();

        if (paiement.estUnEffet()) {
            // L'effet entre dans le portefeuille : rien n'est encaisse encore.
            paiement.setStatut(StatutPaiement.RECU);
            paiement.setNumeroEffet(request.numeroEffet());
            paiement.setBanqueEmettrice(request.banqueEmettrice());
            paiement.setDateEmission(request.dateEmission());
            paiement.setDateReception(request.dateReception() != null
                    ? request.dateReception() : LocalDate.now());
            paiement.setDateEcheance(request.dateEcheance());
        } else {
            paiement.setStatut(StatutPaiement.ENCAISSE);
            paiement.setDateEncaissement(LocalDate.now());
        }

        exigerMontantDisponible(facture, request.montant());
        Paiement sauvegarde = paiementRepository.save(paiement);
        repercuterSurLaFacture(facture);
        return toResponse(sauvegarde);
    }

    @Override
    public PaiementResponse deposer(Long id, LocalDate dateRemise) {
        Paiement paiement = getEffet(id);
        exigerStatut(paiement, StatutPaiement.RECU);
        paiement.setStatut(StatutPaiement.DEPOSE);
        paiement.setDateRemise(dateRemise != null ? dateRemise : LocalDate.now());
        return toResponse(paiementRepository.save(paiement));
    }

    @Override
    public PaiementResponse encaisser(Long id, LocalDate dateEncaissement) {
        Paiement paiement = getEffet(id);
        if (paiement.getStatut() == StatutPaiement.ENCAISSE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet effet est deja encaisse");
        }
        exigerStatutParmi(paiement, EN_ATTENTE);
        paiement.setStatut(StatutPaiement.ENCAISSE);
        paiement.setDateEncaissement(dateEncaissement != null ? dateEncaissement : LocalDate.now());
        paiement.setMotifRejet(null);
        Paiement sauvegarde = paiementRepository.save(paiement);
        repercuterSurLaFacture(paiement.getFacture());
        return toResponse(sauvegarde);
    }

    /**
     * Effet revenu impaye. Le montant retombe, la facture redevient due — et le
     * client peut se retrouver a nouveau au-dessus de son plafond, donc bloque.
     */
    @Override
    public PaiementResponse rejeter(Long id, String motif) {
        Paiement paiement = getEffet(id);
        if (paiement.getStatut() == StatutPaiement.REJETE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet effet est deja rejete");
        }
        paiement.setStatut(StatutPaiement.REJETE);
        paiement.setMotifRejet(motif);
        paiement.setDateEncaissement(null);
        Paiement sauvegarde = paiementRepository.save(paiement);
        repercuterSurLaFacture(paiement.getFacture());

        // Un effet rejete, c'est de l'argent qu'on croyait encaisse et qui ne
        // l'est pas : la facture redevient due et le client peut basculer en
        // bloque. Le commercial doit le savoir avant de le rappeler.
        Facture facture = paiement.getFacture();
        notifications.auCommercial(
                facture.getClient() != null ? facture.getClient().getCommercial() : null,
                new NotificationService.Alerte(
                        TypeNotification.PAIEMENT_REJETE, NiveauNotification.URGENT,
                        "Effet rejete sur " + facture.getNumero(),
                        (paiement.getNumeroEffet() != null
                                ? paiement.getNumeroEffet() + " — " : "")
                                + paiement.getMontant() + " DH rejete"
                                + (motif != null && !motif.isBlank() ? " : " + motif : "."),
                        TypeDocument.FACTURE, facture.getId()));
        return toResponse(sauvegarde);
    }

    /**
     * Retire un paiement saisi par erreur, ou un effet rejete devenu inutile.
     *
     * <p>Un encaissement ne s'efface pas : l'argent est arrive, et le faire
     * disparaitre masquerait un mouvement reel. Il faut d'abord le rejeter, ce
     * qui laisse trace du motif, avant de pouvoir le supprimer.
     */
    @Override
    public void supprimer(Long id) {
        Paiement paiement = paiementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement", id));
        if (paiement.getStatut() == StatutPaiement.ENCAISSE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce paiement est encaisse : rejetez-le d'abord si les fonds sont "
                            + "revenus, sinon il resterait un mouvement sans explication");
        }
        Facture facture = paiement.getFacture();
        paiementRepository.delete(paiement);
        if (facture != null) {
            repercuterSurLaFacture(facture);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaiementResponse> listerParFacture(Long factureId) {
        if (!factureRepository.existsById(factureId)) {
            throw new ResourceNotFoundException("Facture", factureId);
        }
        return paiementRepository.findByFactureId(factureId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Portefeuille d'effets : ce qui est en attente d'encaissement. */
    @Override
    @Transactional(readOnly = true)
    public List<PaiementResponse> portefeuilleEffets() {
        return paiementRepository.effetsEnAttente().stream()
                .map(this::toResponse)
                .toList();
    }

    // --- Regles ---

    /**
     * Recalcule le montant paye depuis les seuls encaissements, puis en tire les
     * consequences : statut de la facture, et blocage du client si son encours
     * repasse au-dessus de son plafond.
     */
    private void repercuterSurLaFacture(Facture facture) {
        BigDecimal encaisse = paiementRepository.totalEncaisse(facture.getId());
        facture.setMontantPaye(encaisse);
        facture.recalculerStatut();
        factureRepository.save(facture);
        if (facture.getClient() != null) {
            clientService.reevaluerBlocage(facture.getClient().getId());
        }
    }

    /**
     * Le total encaisse et en attente ne peut pas depasser la facture : sans ce
     * controle, deux cheques couvrant chacun la totalite passeraient, et le
     * second ne serait refuse qu'a l'encaissement.
     */
    private void exigerMontantDisponible(Facture facture, BigDecimal montant) {
        BigDecimal deja = paiementRepository.totalEngage(facture.getId());
        BigDecimal reste = facture.getMontantTTC().subtract(deja);
        if (montant.compareTo(reste) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le montant depasse le reste a payer (" + reste
                            + "), effets en attente d'encaissement compris");
        }
    }

    private Paiement getEffet(Long id) {
        Paiement paiement = paiementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement", id));
        if (!paiement.estUnEffet()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seuls un cheque ou une traite suivent un cycle d'encaissement");
        }
        return paiement;
    }

    private void exigerStatut(Paiement paiement, StatutPaiement attendu) {
        if (paiement.getStatut() != attendu) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cet effet est " + paiement.getStatut() + " : operation impossible");
        }
    }

    private void exigerStatutParmi(Paiement paiement, Set<StatutPaiement> attendus) {
        if (!attendus.contains(paiement.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cet effet est " + paiement.getStatut() + " : operation impossible");
        }
    }

    private PaiementResponse toResponse(Paiement p) {
        Facture f = p.getFacture();
        return new PaiementResponse(
                p.getId(),
                f != null ? f.getId() : null,
                f != null ? f.getNumero() : null,
                f != null && f.getClient() != null ? f.getClient().getNom() : null,
                p.getDatePaiement(),
                p.getMontant(),
                p.getModePaiement(),
                p.getStatut(),
                p.getReference(),
                p.estUnEffet(),
                p.getNumeroEffet(),
                p.getBanqueEmettrice(),
                p.getDateEmission(),
                p.getDateReception(),
                p.getDateEcheance(),
                p.getDateRemise(),
                p.getDateEncaissement(),
                p.getMotifRejet());
    }
}
