package com.example.gestioncommerciale.service.impl;

import com.example.gestioncommerciale.dto.CommandeRequest;
import com.example.gestioncommerciale.dto.CommandeResponse;
import com.example.gestioncommerciale.dto.LigneCommandeRequest;
import com.example.gestioncommerciale.dto.LigneDocumentResponse;
import com.example.gestioncommerciale.dto.ModificationLignesRequest;
import com.example.gestioncommerciale.dto.MouvementRequest;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.ValidationCommandeRequest;
import com.example.gestioncommerciale.dto.filter.CommandeFilter;
import com.example.gestioncommerciale.entity.Client;
import com.example.gestioncommerciale.entity.Commande;
import com.example.gestioncommerciale.entity.Depot;
import com.example.gestioncommerciale.entity.Devis;
import com.example.gestioncommerciale.entity.LigneCommande;
import com.example.gestioncommerciale.entity.LigneDevis;
import com.example.gestioncommerciale.entity.NiveauNotification;
import com.example.gestioncommerciale.entity.TypeDocument;
import com.example.gestioncommerciale.entity.TypeNotification;
import com.example.gestioncommerciale.entity.Produit;
import com.example.gestioncommerciale.entity.Role;
import com.example.gestioncommerciale.entity.StatutCommande;
import com.example.gestioncommerciale.entity.StatutDevis;
import com.example.gestioncommerciale.entity.Utilisateur;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.ClientRepository;
import com.example.gestioncommerciale.repository.CommandeRepository;
import com.example.gestioncommerciale.repository.DepotRepository;
import com.example.gestioncommerciale.repository.DevisRepository;
import com.example.gestioncommerciale.repository.ProduitRepository;
import com.example.gestioncommerciale.security.CurrentUserService;
import com.example.gestioncommerciale.service.CommandeService;
import com.example.gestioncommerciale.service.NotificationService;
import com.example.gestioncommerciale.service.NumeroDocument;
import com.example.gestioncommerciale.service.PolitiquePouvoirs;
import com.example.gestioncommerciale.service.StockService;
import com.example.gestioncommerciale.specification.CommandeSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class CommandeServiceImpl implements CommandeService {

    private static final BigDecimal CENT = new BigDecimal("100");

    // Statuts ou le stock est reserve : promis a la commande, encore en depot.
    private static final Set<StatutCommande> STOCK_RESERVE = Set.of(
            StatutCommande.VALIDEE, StatutCommande.EN_PREPARATION);

    private final CommandeRepository commandeRepository;
    private final DevisRepository devisRepository;
    private final DepotRepository depotRepository;
    private final ProduitRepository produitRepository;
    private final ClientRepository clientRepository;
    private final StockService stockService;
    private final CurrentUserService currentUserService;
    private final PolitiquePouvoirs politiquePouvoirs;
    private final NotificationService notifications;

    public CommandeServiceImpl(CommandeRepository commandeRepository,
                               DevisRepository devisRepository,
                               DepotRepository depotRepository,
                               ProduitRepository produitRepository,
                               ClientRepository clientRepository,
                               StockService stockService,
                               CurrentUserService currentUserService,
                               PolitiquePouvoirs politiquePouvoirs,
                               NotificationService notifications) {
        this.commandeRepository = commandeRepository;
        this.devisRepository = devisRepository;
        this.depotRepository = depotRepository;
        this.produitRepository = produitRepository;
        this.clientRepository = clientRepository;
        this.stockService = stockService;
        this.currentUserService = currentUserService;
        this.politiquePouvoirs = politiquePouvoirs;
        this.notifications = notifications;
    }

    // La remise d'un devis accepte a deja franchi le controle : la commande qui
    // en decoule n'a pas a le repasser.
    @Override
    public CommandeResponse creerDepuisDevis(Long devisId) {
        Devis devis = devisRepository.findById(devisId)
                .orElseThrow(() -> new ResourceNotFoundException("Devis", devisId));
        // Le devis appartient-il bien au portefeuille de l'utilisateur ?
        exigerAcces(devis.getClient());

        if (devis.getStatut() != StatutDevis.ACCEPTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seul un devis ACCEPTE peut etre transforme en commande");
        }
        if (commandeRepository.existsByDevisId(devisId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une commande existe deja pour ce devis");
        }
        // Un client bloque (plafond de credit depasse) ne peut plus commander
        // tant qu'un administrateur ne l'a pas debloque.
        exigerClientNonBloque(devis.getClient());

        Commande commande = Commande.builder()
                .numero(genererNumero())
                .statut(StatutCommande.EN_ATTENTE)
                .devis(devis)
                .client(devis.getClient())
                .commercial(devis.getCommercial())
                .montantHT(devis.getMontantHT())
                .montantTTC(devis.getMontantTTC())
                .build();

        for (LigneDevis ld : devis.getLignes()) {
            LigneCommande lc = LigneCommande.builder()
                    .produit(ld.getProduit())
                    .designation(ld.getDesignation())
                    .quantite(ld.getQuantite())
                    .prixUnitaire(ld.getPrixUnitaire())
                    .tauxTVA(ld.getTauxTVA())
                    .remise(ld.getRemise())
                    .montantLigne(ld.getMontantLigne())
                    .build();
            commande.ajouterLigne(lc);
        }

        return toResponse(commandeRepository.save(commande));
    }

    @Override
    @Transactional(readOnly = true)
    public CommandeResponse trouverParId(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommandeResponse> lister(CommandeFilter filtre, Pageable pageable) {
        Specification<Commande> spec = CommandeSpecifications.avecFiltre(filtre);
        // Un commercial ne voit que les documents de ses propres clients.
        Long restriction = currentUserService.restrictionAuCommercial();
        if (restriction != null) {
            spec = spec.and((root, q, cb) -> cb.equal(
                    root.get("client").get("commercial").get("id"), restriction));
        }
        return PageResponse.from(
                commandeRepository.findAll(spec, pageable)
                        .map(this::toResponse));
    }

    @Override
    public CommandeResponse valider(Long id, ValidationCommandeRequest request) {
        Commande commande = getOrThrow(id);
        if (commande.getStatut() != StatutCommande.EN_ATTENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seule une commande EN_ATTENTE peut etre validee (statut actuel : "
                            + commande.getStatut() + ")");
        }

        // Depot choisi pour chaque ligne (ligneId -> code de depot)
        Map<Long, String> depotParLigne = request.lignes().stream()
                .collect(Collectors.toMap(
                        ValidationCommandeRequest.LigneDepot::ligneId,
                        ValidationCommandeRequest.LigneDepot::depotCode));

        for (LigneCommande ligne : commande.getLignes()) {
            String depotCode = depotParLigne.get(ligne.getId());
            if (depotCode == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Depot manquant pour la ligne " + ligne.getDesignation());
            }
            Depot depot = depotRepository.findByCode(depotCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Depot", depotCode));

            // Le stock est promis a la commande mais reste physiquement en depot :
            // il ne sortira qu'a la livraison. Un 409 (disponible insuffisant)
            // annule toute la transaction, sans reservation partielle.
            stockService.reserver(ligne.getProduit().getId(), depotCode, ligne.getQuantite());
            ligne.setDepot(depot);
        }

        commande.setStatut(StatutCommande.VALIDEE);
        commande.setDateValidation(LocalDateTime.now());

        // Le stock est reserve : l'entrepot peut preparer. C'est le seul signal
        // qu'attend le magasinier, il ne consulte pas les devis.
        notifications.auxRoles(new NotificationService.Alerte(
                        TypeNotification.COMMANDE_A_PREPARER, NiveauNotification.ALERTE,
                        "Commande " + commande.getNumero() + " a preparer",
                        nomClient(commande.getClient()) + " — "
                                + commande.getLignes().size() + " ligne(s), stock reserve.",
                        TypeDocument.COMMANDE, commande.getId()),
                Role.MAGASINIER);
        return toResponse(commandeRepository.save(commande));
    }

    @Override
    public CommandeResponse creer(CommandeRequest request) {
        Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client", request.clientId()));
        // Un commercial ne travaille que pour ses propres clients.
        exigerAcces(client);
        exigerClientNonBloque(client);

        Commande commande = Commande.builder()
                .numero(genererNumero())
                .statut(StatutCommande.EN_ATTENTE)
                .client(client)
                // Saisie directe : le commercial en charge est celui qui saisit.
                .commercial(currentUserService.getUtilisateurCourant())
                .build();

        remplacerLignes(commande, request.lignes());
        boolean arbitrageAttendu = arbitrerRemise(commande);

        Commande enregistree = commandeRepository.save(commande);
        if (arbitrageAttendu) {
            notifierArbitrageAttendu(enregistree);
        }
        return toResponse(enregistree);
    }

    @Override
    public CommandeResponse modifier(Long id, CommandeRequest request) {
        Commande commande = exigerModifiable(id);

        if (!request.clientId().equals(commande.getClient().getId())) {
            // Changer de client reviendrait a reecrire l'histoire du document :
            // interdit des qu'il decoule d'un devis ou que le stock est engage.
            if (commande.getDevis() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Le client d'une commande issue d'un devis ne peut pas etre change");
            }
            if (commande.getStatut() != StatutCommande.EN_ATTENTE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Le client n'est modifiable que tant que la commande est EN_ATTENTE");
            }
            Client client = clientRepository.findById(request.clientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client", request.clientId()));
            exigerClientNonBloque(client);
            commande.setClient(client);
        }

        remplacerLignes(commande, request.lignes());
        boolean arbitrageAttendu = arbitrerRemise(commande);

        Commande enregistree = commandeRepository.save(commande);
        if (arbitrageAttendu) {
            notifierArbitrageAttendu(enregistree);
        }
        return toResponse(enregistree);
    }

    @Override
    public CommandeResponse modifierLignes(Long id, ModificationLignesRequest request) {
        Commande commande = exigerModifiable(id);
        remplacerLignes(commande, request.lignes());
        boolean arbitrageAttendu = arbitrerRemise(commande);

        Commande enregistree = commandeRepository.save(commande);
        if (arbitrageAttendu) {
            notifierArbitrageAttendu(enregistree);
        }
        return toResponse(enregistree);
    }

    /**
     * Place la commande en attente de validation si une remise depasse le seuil
     * de qui la saisit, et l'en sort des que la remise redescend. Chacun est juge
     * dans la limite de son propre seuil : au-dela, meme un responsable
     * commercial doit en referer.
     *
     * <p>Ne s'applique qu'avant la prise de stock. Une fois la commande validee,
     * la reservation est posee : la renvoyer en arriere demanderait de la
     * defaire, ce que la modification de lignes fait deja ligne a ligne.
     */
    private boolean arbitrerRemise(Commande commande) {
        if (commande.getStatut() != null
                && commande.getStatut() != StatutCommande.EN_ATTENTE
                && commande.getStatut() != StatutCommande.EN_ATTENTE_VALIDATION) {
            return false;
        }
        StatutCommande avant = commande.getStatut();
        boolean couvert = !politiquePouvoirs.depassePouvoirDe(
                currentUserService.getUtilisateurCourant(), commande);
        commande.setStatut(couvert
                ? StatutCommande.EN_ATTENTE : StatutCommande.EN_ATTENTE_VALIDATION);
        // Comme sur le devis : la notification part apres l'enregistrement, une
        // commande neuve n'ayant pas encore d'identifiant a pointer.
        return !couvert && avant != StatutCommande.EN_ATTENTE_VALIDATION;
    }

    /** Previent l'encadrement qu'une remise de commande attend son aval. */
    private void notifierArbitrageAttendu(Commande commande) {
        notifications.auxRoles(new NotificationService.Alerte(
                        TypeNotification.REMISE_A_VALIDER, NiveauNotification.ALERTE,
                        "Remise a arbitrer sur " + commande.getNumero(),
                        "Remise de "
                                + politiquePouvoirs.remiseMax(commande)
                                        .stripTrailingZeros().toPlainString()
                                + " % demandee pour " + nomClient(commande.getClient())
                                + ". La commande est bloquee jusqu'a votre aval.",
                        TypeDocument.COMMANDE, commande.getId()),
                Role.RESPONSABLE_COMMERCIAL, Role.ADMIN);
    }

    @Override
    public CommandeResponse validerRemise(Long id) {
        Commande commande = getOrThrow(id);
        if (commande.getStatut() != StatutCommande.EN_ATTENTE_VALIDATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette commande n'attend pas de validation de remise (statut actuel : "
                            + commande.getStatut() + ")");
        }
        // Un validateur ne couvre pas au-dela de son propre seuil : la remise
        // remonte alors a l'administrateur, qui n'en a pas.
        Utilisateur validateur = currentUserService.getUtilisateurCourant();
        BigDecimal remise = politiquePouvoirs.remiseMax(commande);
        if (politiquePouvoirs.depassePouvoirDe(validateur, remise)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cette remise de " + remise.stripTrailingZeros().toPlainString()
                            + " % depasse votre seuil de "
                            + politiquePouvoirs.seuilDe(validateur).stripTrailingZeros().toPlainString()
                            + " % : seul un administrateur peut la valider");
        }
        commande.setStatut(StatutCommande.EN_ATTENTE);
        commande.setDateValidationRemise(LocalDateTime.now());

        notifications.auCommercial(commande.getCommercial(), new NotificationService.Alerte(
                TypeNotification.REMISE_VALIDEE, NiveauNotification.INFORMATION,
                "Remise validee sur " + commande.getNumero(),
                "Votre remise de " + remise.stripTrailingZeros().toPlainString()
                        + " % est acceptee : la commande peut suivre son cours.",
                TypeDocument.COMMANDE, commande.getId()));
        return toResponse(commandeRepository.save(commande));
    }

    /** Une commande livree ou annulee est close : plus rien a ajuster. */
    private Commande exigerModifiable(Long id) {
        Commande commande = getOrThrow(id);
        if (commande.getStatut() == StatutCommande.LIVREE
                || commande.getStatut() == StatutCommande.ANNULEE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une commande " + commande.getStatut() + " n'est plus modifiable");
        }
        return commande;
    }

    private void exigerClientNonBloque(Client client) {
        if (client.estBloque()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Client bloque : plafond de credit depasse. Deblocage requis avant toute commande.");
        }
    }

    /**
     * Remplace toutes les lignes de la commande et repercute l'ecart sur le
     * stock. Tant que la commande n'est pas validee rien n'est engage ; une fois
     * validee, les reservations du depot de chaque ligne sont ajustees.
     */
    private void remplacerLignes(Commande commande, List<LigneCommandeRequest> lignes) {
        // Conditions deja negociees (devis ou lignes actuelles) : elles priment
        // sur le catalogue pour ne pas changer un prix annonce au client. Tout
        // autre produit du catalogue reste librement ajoutable.
        Map<Long, Modele> modeles = modelesNegocies(commande);

        Set<Long> vus = new HashSet<>();
        for (LigneCommandeRequest l : lignes) {
            if (!vus.add(l.produitId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Le produit " + designation(modeles, l.produitId())
                                + " figure deux fois : regroupez-le sur une seule ligne");
            }
        }

        // Etat courant, avant remplacement : quantite et depot par produit.
        Map<Long, BigDecimal> quantitesAvant = new LinkedHashMap<>();
        Map<Long, Depot> depotsAvant = new LinkedHashMap<>();
        for (LigneCommande lc : commande.getLignes()) {
            quantitesAvant.put(lc.getProduit().getId(), lc.getQuantite());
            if (lc.getDepot() != null) {
                depotsAvant.put(lc.getProduit().getId(), lc.getDepot());
            }
        }
        boolean stockReserve = STOCK_RESERVE.contains(commande.getStatut());

        commande.viderLignes();
        for (LigneCommandeRequest l : lignes) {
            Modele modele = modeles.computeIfAbsent(l.produitId(), this::modeleCatalogue);
            // Conditions saisies si elles sont fournies, sinon celles du devis
            // d'origine ou du catalogue. Un commercial ne fixe ni prix ni TVA :
            // ses valeurs sont ignorees au profit des conditions de reference.
            boolean imposes = currentUserService.prixImposes();
            BigDecimal prixUnitaire = premierNonNul(
                    imposes ? null : l.prixUnitaire(), modele.prixUnitaire());
            BigDecimal tauxTVA = premierNonNul(
                    imposes ? null : l.tauxTVA(), modele.tauxTVA());
            BigDecimal remise = premierNonNul(l.remise(), modele.remise());
            LigneCommande ligne = LigneCommande.builder()
                    .produit(modele.produit())
                    .designation(modele.designation())
                    .quantite(l.quantite())
                    .prixUnitaire(prixUnitaire)
                    .tauxTVA(tauxTVA)
                    .remise(remise)
                    .montantLigne(montantLigneHT(prixUnitaire, l.quantite(), remise))
                    .depot(depotsAvant.get(l.produitId()))
                    .build();
            commande.ajouterLigne(ligne);

            if (stockReserve) {
                ajusterReservation(ligne, quantitesAvant.get(l.produitId()), l.depotCode());
            }
            quantitesAvant.remove(l.produitId());
        }

        // Produits retires d'une commande validee : leur reservation est rendue.
        if (stockReserve) {
            for (Map.Entry<Long, BigDecimal> reste : quantitesAvant.entrySet()) {
                Depot depot = depotsAvant.get(reste.getKey());
                if (depot != null) {
                    stockService.libererReservation(reste.getKey(), depot.getCode(), reste.getValue());
                }
            }
        }

        calculerMontants(commande);
    }

    /**
     * Commande validee : le stock est reserve, pas encore sorti. On repercute la
     * difference sur la reservation du depot de la ligne.
     */
    private void ajusterReservation(LigneCommande ligne, BigDecimal avant, String depotCode) {
        BigDecimal precedent = avant != null ? avant : BigDecimal.ZERO;
        int sens = ligne.getQuantite().compareTo(precedent);

        Depot depot = ligne.getDepot();
        if (depot == null) {
            // Produit ajoute apres la validation : son depot de prelevement n'est
            // pas encore connu, l'appelant doit le preciser.
            if (depotCode == null || depotCode.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cette commande est validee : precisez le depot de prelevement pour "
                                + ligne.getDesignation());
            }
            depot = depotRepository.findByCode(depotCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Depot", depotCode));
            ligne.setDepot(depot);
        }
        if (sens == 0) {
            return;
        }

        Long produitId = ligne.getProduit().getId();
        if (sens > 0) {
            // Refus si le disponible du depot ne couvre pas le supplement.
            stockService.reserver(produitId, depot.getCode(),
                    ligne.getQuantite().subtract(precedent));
        } else {
            stockService.libererReservation(produitId, depot.getCode(),
                    precedent.subtract(ligne.getQuantite()));
        }
    }

    /**
     * Conditions deja negociees, par produit : celles du devis d'origine
     * completees par celles des lignes actuelles de la commande.
     */
    private Map<Long, Modele> modelesNegocies(Commande commande) {
        Map<Long, Modele> modeles = new LinkedHashMap<>();
        if (commande.getDevis() != null) {
            for (LigneDevis ld : commande.getDevis().getLignes()) {
                modeles.put(ld.getProduit().getId(), new Modele(ld.getProduit(), ld.getDesignation(),
                        ld.getPrixUnitaire(), ld.getTauxTVA(), ld.getRemise()));
            }
        }
        for (LigneCommande lc : commande.getLignes()) {
            modeles.putIfAbsent(lc.getProduit().getId(), new Modele(lc.getProduit(), lc.getDesignation(),
                    lc.getPrixUnitaire(), lc.getTauxTVA(), lc.getRemise()));
        }
        return modeles;
    }

    /** Produit ajoute librement : conditions reprises du catalogue. */
    private Modele modeleCatalogue(Long produitId) {
        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new ResourceNotFoundException("Produit", produitId));
        return new Modele(produit, produit.getDesignation(),
                produit.getPrixUnitaireHT(), produit.getTauxTVA(), BigDecimal.ZERO);
    }

    /** Conditions figees d'un produit, reprises a l'identique sur la commande. */
    private record Modele(Produit produit, String designation, BigDecimal prixUnitaire,
                          BigDecimal tauxTVA, BigDecimal remise) {
    }

    private String designation(Map<Long, Modele> modeles, Long produitId) {
        Modele modele = modeles.get(produitId);
        return modele != null ? modele.designation() : ("#" + produitId);
    }

    /** Premiere valeur renseignee, 0 si aucune. */
    private BigDecimal premierNonNul(BigDecimal saisie, BigDecimal defaut) {
        if (saisie != null) {
            return saisie;
        }
        return defaut != null ? defaut : BigDecimal.ZERO;
    }

    /** Montant HT d'une ligne, remise (en %) deduite. */
    private BigDecimal montantLigneHT(BigDecimal prixUnitaire, BigDecimal quantite, BigDecimal remisePct) {
        BigDecimal brut = prixUnitaire.multiply(quantite);
        BigDecimal facteurRemise = BigDecimal.ONE.subtract(remisePct.divide(CENT));
        return brut.multiply(facteurRemise).setScale(2, RoundingMode.HALF_UP);
    }

    private void calculerMontants(Commande commande) {
        BigDecimal totalHT = BigDecimal.ZERO;
        BigDecimal totalTVA = BigDecimal.ZERO;
        for (LigneCommande ligne : commande.getLignes()) {
            totalHT = totalHT.add(ligne.getMontantLigne());
            BigDecimal taux = ligne.getTauxTVA() != null ? ligne.getTauxTVA() : BigDecimal.ZERO;
            totalTVA = totalTVA.add(ligne.getMontantLigne().multiply(taux).divide(CENT));
        }
        totalHT = totalHT.setScale(2, RoundingMode.HALF_UP);
        commande.setMontantHT(totalHT);
        commande.setMontantTTC(totalHT.add(totalTVA).setScale(2, RoundingMode.HALF_UP));
    }

    @Override
    public CommandeResponse changerStatut(Long id, String statut) {
        Commande commande = getOrThrow(id);
        StatutCommande nouveau = parseStatut(statut);
        StatutCommande actuel = commande.getStatut();

        // Une remise non tranchee gele la commande : seule l'annulation reste
        // ouverte, et seule la validation de la remise permet d'en sortir.
        if (actuel == StatutCommande.EN_ATTENTE_VALIDATION
                && nouveau != StatutCommande.ANNULEE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La remise de cette commande doit d'abord etre validee par le "
                            + "responsable commercial");
        }

        // La validation (prise de stock) passe par valider() qui exige les depots.
        if (nouveau == StatutCommande.VALIDEE && actuel == StatutCommande.EN_ATTENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Pour valider une commande, utilisez la validation avec choix des depots");
        }

        exigerDroitSurTransition(nouveau);

        // Livraison : la marchandise part reellement. La reservation devient une
        // sortie de stock sur le depot retenu a la validation.
        if (nouveau == StatutCommande.LIVREE && STOCK_RESERVE.contains(actuel)) {
            livrerStock(commande);
        }

        // Annulation : on rend ce qui etait engage, selon l'etat d'origine.
        if (nouveau == StatutCommande.ANNULEE) {
            if (actuel == StatutCommande.LIVREE) {
                restituerStock(commande);
            } else if (STOCK_RESERVE.contains(actuel)) {
                libererReservations(commande);
            }
        }


        commande.setStatut(nouveau);
        dater(commande, nouveau);
        // La livraison passe la main a la comptabilite : la commande devient
        // facturable, et personne d'autre ne surveille ce moment.
        if (nouveau == StatutCommande.LIVREE) {
            notifications.auxRoles(new NotificationService.Alerte(
                            TypeNotification.COMMANDE_A_FACTURER, NiveauNotification.ALERTE,
                            "Commande " + commande.getNumero() + " livree, a facturer",
                            nomClient(commande.getClient()) + " — "
                                    + montant(commande.getMontantTTC()) + " TTC.",
                            TypeDocument.COMMANDE, commande.getId()),
                    Role.COMPTABLE);
        }
        return toResponse(commandeRepository.save(commande));
    }

    /** Montant lisible dans un message de notification. */
    private String montant(BigDecimal valeur) {
        return valeur == null ? "0" : valeur.setScale(2, RoundingMode.HALF_UP).toPlainString() + " DH";
    }

    /**
     * Pose la date de l'etape qui vient d'etre franchie. Elle n'est ecrite
     * qu'une fois : un aller-retour de statut ne doit pas effacer la date de
     * premiere prise en charge, qui est celle qui fait foi pour les delais.
     */
    private void dater(Commande commande, StatutCommande nouveau) {
        LocalDateTime maintenant = LocalDateTime.now();
        switch (nouveau) {
            case EN_PREPARATION -> {
                if (commande.getDateEnPreparation() == null) {
                    commande.setDateEnPreparation(maintenant);
                }
            }
            case LIVREE -> {
                if (commande.getDateLivraison() == null) {
                    commande.setDateLivraison(maintenant);
                }
            }
            case ANNULEE -> commande.setDateAnnulation(maintenant);
            default -> { }
        }
    }

    /**
     * Qui a le droit de provoquer ce changement d'etat.
     *
     * <p>Preparer et livrer relevent de l'entrepot : le magasinier (et l'admin)
     * en sont seuls maitres. Annuler releve au contraire de la relation client,
     * donc de la vente. Chacun reste ainsi dans son metier.
     */
    private void exigerDroitSurTransition(StatutCommande nouveau) {
        boolean autorise = switch (nouveau) {
            case EN_PREPARATION, LIVREE -> currentUserService.aRole(Role.ADMIN, Role.MAGASINIER);
            case ANNULEE -> currentUserService.aRole(
                    Role.ADMIN, Role.RESPONSABLE_COMMERCIAL, Role.COMMERCIAL);
            default -> true;
        };
        if (!autorise) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    nouveau == StatutCommande.ANNULEE
                            ? "L'annulation d'une commande releve du service commercial"
                            : "La preparation et la livraison relevent du magasinier");
        }
    }

    /** Sort physiquement le stock reserve, dans le depot de chaque ligne. */
    private void livrerStock(Commande commande) {
        for (LigneCommande ligne : commande.getLignes()) {
            if (ligne.getDepot() == null) {
                continue;
            }
            String depotCode = ligne.getDepot().getCode();
            // Liberer avant de sortir : la sortie refuse d'entamer le reserve.
            stockService.libererReservation(ligne.getProduit().getId(), depotCode, ligne.getQuantite());
            stockService.sortie(new MouvementRequest(
                    ligne.getProduit().getId(),
                    depotCode,
                    ligne.getQuantite(),
                    "Livraison commande " + commande.getNumero()));
        }
    }

    /** Rend le disponible d'une commande validee mais jamais livree. */
    private void libererReservations(Commande commande) {
        for (LigneCommande ligne : commande.getLignes()) {
            if (ligne.getDepot() != null) {
                stockService.libererReservation(ligne.getProduit().getId(),
                        ligne.getDepot().getCode(), ligne.getQuantite());
            }
        }
    }

    /** Remet en stock les quantites livrees, dans le depot de chaque ligne. */
    private void restituerStock(Commande commande) {
        for (LigneCommande ligne : commande.getLignes()) {
            if (ligne.getDepot() == null) {
                continue;
            }
            stockService.entree(new MouvementRequest(
                    ligne.getProduit().getId(),
                    ligne.getDepot().getCode(),
                    ligne.getQuantite(),
                    "Annulation commande " + commande.getNumero()));
        }
    }

    @Override
    public void supprimer(Long id) {
        Commande commande = getOrThrow(id);
        // Ne pas perdre le stock livre, ni laisser une reservation orpheline.
        if (commande.getStatut() == StatutCommande.LIVREE) {
            restituerStock(commande);
        } else if (STOCK_RESERVE.contains(commande.getStatut())) {
            libererReservations(commande);
        }
        commandeRepository.delete(commande);
    }

    // --- Helpers ---

    private Commande getOrThrow(Long id) {
        Commande document = commandeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commande", id));
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

    private StatutCommande parseStatut(String statut) {
        try {
            return StatutCommande.valueOf(statut);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Statut de commande invalide : " + statut);
        }
    }

    private String genererNumero() {
        String prefix = "CMD-" + Year.now().getValue() + "-";
        return NumeroDocument.suivant(prefix, commandeRepository.dernierNumero(prefix));
    }

    private CommandeResponse toResponse(Commande c) {
        Client client = c.getClient();
        Utilisateur com = c.getCommercial();
        List<LigneDocumentResponse> lignes = c.getLignes().stream()
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
                        l.getDepot() != null ? l.getDepot().getCode() : null))
                .toList();
        return new CommandeResponse(
                c.getId(),
                c.getNumero(),
                c.getDateCommande(),
                c.getStatut(),
                c.getDateValidation(),
                c.getDateValidationRemise(),
                c.getDateEnPreparation(),
                c.getDateLivraison(),
                c.getDateAnnulation(),
                c.getMontantHT(),
                c.getMontantTTC(),
                c.getDevis() != null ? c.getDevis().getId() : null,
                c.getDevis() != null ? c.getDevis().getNumero() : null,
                client != null ? client.getId() : null,
                client != null ? nomClient(client) : null,
                com != null ? com.getId() : null,
                com != null ? com.getPrenom() + " " + com.getNom() : null,
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
