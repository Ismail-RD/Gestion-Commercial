package com.example.gestioncommerciale.service.impl;

import com.example.gestioncommerciale.dto.FactureModificationRequest;
import com.example.gestioncommerciale.dto.FactureRequest;
import com.example.gestioncommerciale.dto.FactureResponse;
import com.example.gestioncommerciale.dto.LigneDocumentResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.filter.FactureFilter;
import com.example.gestioncommerciale.entity.Client;
import com.example.gestioncommerciale.entity.Commande;
import com.example.gestioncommerciale.entity.StatutCommande;
import com.example.gestioncommerciale.entity.Facture;
import com.example.gestioncommerciale.entity.LigneCommande;
import com.example.gestioncommerciale.entity.LigneFacture;
import com.example.gestioncommerciale.entity.StatutFacture;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.CommandeRepository;
import com.example.gestioncommerciale.repository.FactureRepository;
import com.example.gestioncommerciale.repository.PaiementRepository;
import com.example.gestioncommerciale.security.CurrentUserService;
import com.example.gestioncommerciale.service.ClientService;
import com.example.gestioncommerciale.service.FactureService;
import com.example.gestioncommerciale.service.NumeroDocument;
import com.example.gestioncommerciale.specification.FactureSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;

@Service
@Transactional
public class FactureServiceImpl implements FactureService {

    private final FactureRepository factureRepository;
    private final CommandeRepository commandeRepository;
    private final PaiementRepository paiementRepository;
    private final ClientService clientService;
    private final CurrentUserService currentUserService;

    public FactureServiceImpl(FactureRepository factureRepository,
                              PaiementRepository paiementRepository,
                              CommandeRepository commandeRepository,
                              ClientService clientService,
                              CurrentUserService currentUserService) {
        this.factureRepository = factureRepository;
        this.paiementRepository = paiementRepository;
        this.commandeRepository = commandeRepository;
        this.clientService = clientService;
        this.currentUserService = currentUserService;
    }

    @Override
    public FactureResponse creerDepuisCommande(FactureRequest request) {
        Commande commande = commandeRepository.findById(request.commandeId())
                .orElseThrow(() -> new ResourceNotFoundException("Commande", request.commandeId()));

        if (commande.getStatut() == StatutCommande.EN_ATTENTE_VALIDATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La remise de la commande " + commande.getNumero() + " doit d'abord "
                            + "etre validee par le responsable commercial");
        }

        // Une commande ne se facture qu'une fois : deux factures pour la meme
        // livraison doubleraient l'encours du client et le chiffre d'affaires.
        // Pour refacturer, il faut d'abord supprimer la facture existante.
        factureRepository.findFirstByCommandeId(commande.getId()).ifPresent(existante -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La commande " + commande.getNumero() + " est deja facturee ("
                            + existante.getNumero() + ")");
        });

        Facture facture = Facture.builder()
                .numero(genererNumero())
                .dateEcheance(request.dateEcheance())
                .statut(StatutFacture.EMISE)
                .montantHT(commande.getMontantHT())
                .montantTTC(commande.getMontantTTC())
                .montantPaye(BigDecimal.ZERO)
                .commande(commande)
                .client(commande.getClient())
                .build();
        // Une facture emise avec une echeance deja passee nait en retard.
        facture.recalculerStatut();

        for (LigneCommande lc : commande.getLignes()) {
            LigneFacture lf = LigneFacture.builder()
                    .produit(lc.getProduit())
                    .designation(lc.getDesignation())
                    .quantite(lc.getQuantite())
                    .prixUnitaire(lc.getPrixUnitaire())
                    .tauxTVA(lc.getTauxTVA())
                    .remise(lc.getRemise())
                    .montantLigne(lc.getMontantLigne())
                    .build();
            facture.ajouterLigne(lf);
        }

        Facture enregistree = factureRepository.save(facture);
        // Une nouvelle facture augmente l'encours : on reevalue le blocage credit.
        clientService.reevaluerBlocage(commande.getClient().getId());
        return toResponse(enregistree);
    }

    @Override
    @Transactional(readOnly = true)
    public FactureResponse trouverParId(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FactureResponse> lister(FactureFilter filtre, Pageable pageable) {
        Specification<Facture> spec = FactureSpecifications.avecFiltre(filtre);
        // Un commercial ne voit que les documents de ses propres clients.
        Long restriction = currentUserService.restrictionAuCommercial();
        if (restriction != null) {
            spec = spec.and((root, q, cb) -> cb.equal(
                    root.get("client").get("commercial").get("id"), restriction));
        }
        return PageResponse.from(
                factureRepository.findAll(spec, pageable)
                        .map(this::toResponse));
    }

    @Override
    public FactureResponse modifier(Long id, FactureModificationRequest request) {
        Facture facture = getOrThrow(id);
        // Les montants et les lignes decoulent de la commande facturee : seule
        // l'echeance se renegocie. Repoussee, elle sort la facture du retard ;
        // avancee dans le passe, elle l'y met.
        facture.setDateEcheance(request.dateEcheance());
        facture.recalculerStatut();
        return toResponse(factureRepository.save(facture));
    }

    @Override
    public void supprimer(Long id) {
        Facture facture = getOrThrow(id);
        // Le test porte sur l'existence des paiements, pas sur leur montant : un
        // effet rejete ne pese rien mais garde sa ligne, et effacer la facture
        // laisserait une reference orpheline.
        BigDecimal encaisse = paiementRepository.totalEncaisse(id);
        if (encaisse.signum() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette facture a deja recu des reglements (" + encaisse
                            + ") : elle ne peut pas etre supprimee");
        }
        long rattaches = paiementRepository.countByFactureId(id);
        if (rattaches > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    rattaches + " paiement(s) sont rattaches a cette facture, dont "
                            + "d'eventuels effets rejetes ou en attente. Supprimez-les "
                            + "d'abord depuis le detail de la facture.");
        }
        factureRepository.delete(facture);
    }

    // --- Helpers ---

    private Facture getOrThrow(Long id) {
        Facture document = factureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", id));
        exigerAcces(document.getClient());
        return document;
    }

    /**
     * Un commercial n'accede qu'aux documents de ses clients : sans ce garde,
     * un identifiant devine suffirait a consulter le dossier d'un collegue.
     */
    private void exigerAcces(com.example.gestioncommerciale.entity.Client client) {
        Long restriction = currentUserService.restrictionAuCommercial();
        if (restriction == null) {
            return;
        }
        Long titulaire = client != null && client.getCommercial() != null
                ? client.getCommercial().getId() : null;
        if (!restriction.equals(titulaire)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Ce document appartient a un autre commercial");
        }
    }

    private String genererNumero() {
        String prefix = "FAC-" + Year.now().getValue() + "-";
        return NumeroDocument.suivant(prefix, factureRepository.dernierNumero(prefix));
    }

    private FactureResponse toResponse(Facture f) {
        Client client = f.getClient();
        BigDecimal paye = f.getMontantPaye() != null ? f.getMontantPaye() : BigDecimal.ZERO;
        BigDecimal reste = f.getMontantTTC() != null ? f.getMontantTTC().subtract(paye) : BigDecimal.ZERO;
        List<LigneDocumentResponse> lignes = f.getLignes().stream()
                .map(l -> new LigneDocumentResponse(
                        l.getId(),
                        l.getProduit() != null ? l.getProduit().getId() : null,
                        l.getProduit() != null ? l.getProduit().getReference() : null,
                        l.getDesignation(),
                        l.getQuantite(),
                        l.getPrixUnitaire(),
                        l.getTauxTVA(),
                        l.getRemise(),
                        l.getMontantLigne(),
                        null))
                .toList();
        return new FactureResponse(
                f.getId(),
                f.getNumero(),
                f.getDateFacture(),
                f.getDateEcheance(),
                f.getDateReglement(),
                f.getStatut(),
                f.getMontantHT(),
                f.getMontantTTC(),
                paye,
                reste,
                f.getDateEnvoiEmail(),
                f.getCommande() != null ? f.getCommande().getId() : null,
                f.getCommande() != null ? f.getCommande().getNumero() : null,
                client != null ? client.getId() : null,
                client != null ? nomClient(client) : null,
                lignes
        );
    }

    private String nomClient(Client c) {
        // Relation LAZY : materialiser le proxy avant instanceof.
        Client client = (Client) org.hibernate.Hibernate.unproxy(c);
        String prenom = null;
        if (client instanceof com.example.gestioncommerciale.entity.ClientParticulier p) {
            prenom = p.getPrenom();
        }
        return prenom != null ? prenom + " " + client.getNom() : client.getNom();
    }
}
