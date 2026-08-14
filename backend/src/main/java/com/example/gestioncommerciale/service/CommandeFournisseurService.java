package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.CommandeFournisseurRequest;
import com.example.gestioncommerciale.dto.CommandeFournisseurResponse;
import com.example.gestioncommerciale.dto.LigneCommandeFournisseurRequest;
import com.example.gestioncommerciale.dto.LigneCommandeFournisseurResponse;
import com.example.gestioncommerciale.dto.MouvementRequest;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.ReceptionCommandeFournisseurRequest;
import com.example.gestioncommerciale.entity.CommandeFournisseur;
import com.example.gestioncommerciale.entity.Depot;
import com.example.gestioncommerciale.entity.Fournisseur;
import com.example.gestioncommerciale.entity.LigneCommandeFournisseur;
import com.example.gestioncommerciale.entity.Produit;
import com.example.gestioncommerciale.entity.ProduitFournisseur;
import com.example.gestioncommerciale.entity.StatutCommandeFournisseur;
import com.example.gestioncommerciale.entity.Utilisateur;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.CommandeFournisseurRepository;
import com.example.gestioncommerciale.repository.DepotRepository;
import com.example.gestioncommerciale.repository.FournisseurRepository;
import com.example.gestioncommerciale.repository.ProduitRepository;
import com.example.gestioncommerciale.repository.StockProduitRepository;
import com.example.gestioncommerciale.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Commandes passees aux fournisseurs, du bon de commande a l'entree en stock.
 *
 * <p>Deux regles portent tout le reste. D'abord, une commande emise ne se
 * retouche plus : le bon de commande est parti chez le fournisseur, et modifier
 * ses lignes ferait diverger la base de ce qu'il a recu. Ensuite, la reception
 * est le seul moment ou le stock bouge, et elle enregistre ce qui est
 * reellement arrive, pas ce qui avait ete commande.
 */
@Service
@Transactional
public class CommandeFournisseurService {

    /** Etats ou les lignes se retouchent encore : avant l'emission du bon. */
    private static final Set<StatutCommandeFournisseur> MODIFIABLES =
            Set.of(StatutCommandeFournisseur.BROUILLON);

    /** Progression normale du dossier. L'annulation, elle, part de partout. */
    private static final Map<StatutCommandeFournisseur, Set<StatutCommandeFournisseur>> SUITES =
            Map.of(
                    StatutCommandeFournisseur.BROUILLON,
                    Set.of(StatutCommandeFournisseur.COMMANDEE),
                    StatutCommandeFournisseur.COMMANDEE,
                    Set.of(StatutCommandeFournisseur.EN_TRANSIT,
                            StatutCommandeFournisseur.RECEPTIONNEE),
                    StatutCommandeFournisseur.EN_TRANSIT,
                    Set.of(StatutCommandeFournisseur.EN_DOUANE,
                            StatutCommandeFournisseur.RECEPTIONNEE),
                    StatutCommandeFournisseur.EN_DOUANE,
                    Set.of(StatutCommandeFournisseur.RECEPTIONNEE),
                    // Le reliquat peut encore arriver : le dossier reste ouvert.
                    StatutCommandeFournisseur.RECEPTIONNEE_PARTIELLEMENT,
                    Set.of(StatutCommandeFournisseur.RECEPTIONNEE));

    private final CommandeFournisseurRepository commandeRepository;
    private final FournisseurRepository fournisseurRepository;
    private final ProduitRepository produitRepository;
    private final DepotRepository depotRepository;
    private final StockProduitRepository stockProduitRepository;
    private final StockService stockService;
    private final CurrentUserService currentUserService;

    public CommandeFournisseurService(CommandeFournisseurRepository commandeRepository,
                                      FournisseurRepository fournisseurRepository,
                                      ProduitRepository produitRepository,
                                      DepotRepository depotRepository,
                                      StockProduitRepository stockProduitRepository,
                                      StockService stockService,
                                      CurrentUserService currentUserService) {
        this.commandeRepository = commandeRepository;
        this.fournisseurRepository = fournisseurRepository;
        this.produitRepository = produitRepository;
        this.depotRepository = depotRepository;
        this.stockProduitRepository = stockProduitRepository;
        this.stockService = stockService;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public PageResponse<CommandeFournisseurResponse> lister(Pageable pageable) {
        Page<CommandeFournisseur> page = commandeRepository.findAll(pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public CommandeFournisseurResponse trouverParId(Long id) {
        return toResponse(getOrThrow(id));
    }

    public CommandeFournisseurResponse creer(CommandeFournisseurRequest request) {
        CommandeFournisseur commande = CommandeFournisseur.builder()
                .numero(genererNumero())
                .statut(StatutCommandeFournisseur.BROUILLON)
                .acheteur(currentUserService.getUtilisateurCourant())
                .build();
        appliquer(commande, request);
        return toResponse(commandeRepository.save(commande));
    }

    public CommandeFournisseurResponse modifier(Long id, CommandeFournisseurRequest request) {
        CommandeFournisseur commande = getOrThrow(id);
        exigerModifiable(commande);
        commande.viderLignes();
        appliquer(commande, request);
        return toResponse(commandeRepository.save(commande));
    }

    public void supprimer(Long id) {
        CommandeFournisseur commande = getOrThrow(id);
        if (commande.getStatut() != StatutCommandeFournisseur.BROUILLON
                && commande.getStatut() != StatutCommandeFournisseur.ANNULEE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette commande a ete emise : annulez-la plutot que de l'effacer, "
                            + "l'engagement pris aupres du fournisseur doit rester lisible");
        }
        commandeRepository.delete(commande);
    }

    /**
     * Emet le bon de commande. A partir d'ici les lignes sont figees : le
     * fournisseur a recu un document, la base doit continuer de dire la meme
     * chose que lui.
     */
    public CommandeFournisseurResponse emettre(Long id) {
        CommandeFournisseur commande = getOrThrow(id);
        exigerTransition(commande, StatutCommandeFournisseur.COMMANDEE);
        commande.setStatut(StatutCommandeFournisseur.COMMANDEE);
        if (commande.getDateCommande() == null) {
            commande.setDateCommande(LocalDate.now());
        }
        return toResponse(commandeRepository.save(commande));
    }

    public CommandeFournisseurResponse changerStatut(Long id, StatutCommandeFournisseur nouveau) {
        CommandeFournisseur commande = getOrThrow(id);
        if (nouveau == StatutCommandeFournisseur.RECEPTIONNEE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La reception se fait par l'endpoint dedie : elle doit dire ce qui "
                            + "est reellement arrive");
        }
        exigerTransition(commande, nouveau);
        commande.setStatut(nouveau);
        // Le depart et l'arrivee au port se datent : le premier mesure le delai
        // reel du fournisseur, le second fait courir le magasinage.
        if (nouveau == StatutCommandeFournisseur.EN_TRANSIT && commande.getDateTransit() == null) {
            commande.setDateTransit(LocalDate.now());
        }
        if (nouveau == StatutCommandeFournisseur.EN_DOUANE && commande.getDateDouane() == null) {
            commande.setDateDouane(LocalDate.now());
        }
        return toResponse(commandeRepository.save(commande));
    }

    /**
     * Annulation : ouverte tant que la marchandise n'est pas entree en stock.
     * Apres, il faudrait une sortie pour la ressortir, ce qui n'est plus une
     * annulation mais un retour fournisseur.
     */
    public CommandeFournisseurResponse annuler(Long id) {
        CommandeFournisseur commande = getOrThrow(id);
        if (commande.getStatut() == StatutCommandeFournisseur.RECEPTIONNEE
                || commande.getStatut() == StatutCommandeFournisseur.RECEPTIONNEE_PARTIELLEMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "De la marchandise est deja entree en stock sur cette commande : "
                            + "un retour fournisseur passe par une sortie de stock");
        }
        commande.setStatut(StatutCommandeFournisseur.ANNULEE);
        commande.setDateAnnulation(LocalDate.now());
        return toResponse(commandeRepository.save(commande));
    }

    /**
     * Receptionne la marchandise : chaque ligne enregistre ce qui est
     * reellement arrive, et le stock du depot est credite d'autant. Une ligne
     * non mentionnee est consideree recue en totalite.
     */
    public CommandeFournisseurResponse receptionner(
            Long id, ReceptionCommandeFournisseurRequest request) {
        CommandeFournisseur commande = getOrThrow(id);
        exigerTransition(commande, StatutCommandeFournisseur.RECEPTIONNEE);

        Map<Long, BigDecimal> recues = new HashMap<>();
        if (request != null && request.lignes() != null) {
            for (ReceptionCommandeFournisseurRequest.LigneRecue ligne : request.lignes()) {
                recues.put(ligne.ligneId(), ligne.quantiteRecue());
            }
        }

        String depot = commande.getDepotReception().getCode();
        // Ce qui arrive lors de cette livraison, ligne par ligne. Une ligne non
        // mentionnee recoit son reliquat : c'est le cas courant.
        Map<Long, BigDecimal> arrivees = new LinkedHashMap<>();
        for (LigneCommandeFournisseur ligne : commande.getLignes()) {
            BigDecimal reliquat = reliquat(ligne);
            BigDecimal quantite = recues.getOrDefault(ligne.getId(), reliquat);
            if (quantite.signum() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Une quantite recue ne peut pas etre negative");
            }
            if (quantite.compareTo(reliquat) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ligne " + ligne.getProduit().getReference() + " : " + quantite
                                + " recus pour " + reliquat + " encore attendus sur "
                                + ligne.getQuantiteCommandee() + " commandes.");
            }
            arrivees.put(ligne.getId(), quantite);
        }

        if (arrivees.values().stream().allMatch(q -> q.signum() <= 0)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Aucune quantite receptionnee : il n'y a rien a faire entrer en stock");
        }

        Map<Long, BigDecimal> fraisDeLArrivee = repartirFrais(commande, arrivees);

        for (LigneCommandeFournisseur ligne : commande.getLignes()) {
            BigDecimal arrivee = arrivees.get(ligne.getId());
            if (arrivee == null || arrivee.signum() <= 0) {
                continue;
            }
            // Le CUMP se calcule sur le stock d'avant l'entree : une fois la
            // marchandise entree, l'ancienne quantite n'est plus lisible.
            majCoutMoyen(ligne.getProduit(), arrivee, coutDeCetteArrivee(commande, ligne,
                    arrivee, fraisDeLArrivee.getOrDefault(ligne.getId(), BigDecimal.ZERO)));
            ligne.setQuantiteRecue(valeur(ligne.getQuantiteRecue()).add(arrivee));
            stockService.entree(new MouvementRequest(ligne.getProduit().getId(), depot,
                    arrivee, "Reception " + commande.getNumero()));
        }

        recalculerCoutUnitaire(commande);

        // Tant qu'il reste du reliquat, le dossier demeure ouvert : la
        // marchandise manquante peut encore arriver.
        boolean complet = commande.getLignes().stream()
                .allMatch(l -> reliquat(l).signum() <= 0);
        commande.setStatut(complet
                ? StatutCommandeFournisseur.RECEPTIONNEE
                : StatutCommandeFournisseur.RECEPTIONNEE_PARTIELLEMENT);

        // Deux dates distinctes, sinon un dossier livre en deux fois perdrait la
        // premiere : l'arrivee initiale, et la cloture du dossier.
        if (commande.getDatePremiereReception() == null) {
            commande.setDatePremiereReception(LocalDate.now());
        }
        commande.setDateReception(complet ? LocalDate.now() : null);
        return toResponse(commandeRepository.save(commande));
    }

    /** Ce qui reste attendu sur une ligne. */
    private BigDecimal reliquat(LigneCommandeFournisseur ligne) {
        return ligne.getQuantiteCommandee().subtract(valeur(ligne.getQuantiteRecue()));
    }

    // --- Cout de revient ---

    /**
     * Repartit les frais du dossier sur les lignes recues et en deduit le cout
     * debarque de chaque unite.
     *
     * <p>La repartition se fait au prorata de la valeur marchandise. Le prorata
     * se calcule sur ce qui <em>restait a recevoir</em>, pas sur la seule
     * livraison du jour : sinon la premiere arrivee d'un dossier livre en deux
     * fois porterait tous les frais et sortirait a un cout artificiellement
     * eleve. Chaque arrivee prend sa part, la derniere solde le reste.
     *
     * <p>Corollaire : si un reliquat n'arrive jamais, sa part de frais reste
     * non imputee. C'est voulu — mieux vaut un solde en suspens qu'un cout de
     * revient fausse sur la marchandise deja vendue.
     *
     * <p>A defaut de poids sur les produits, la valeur est le seul prorata
     * disponible. Il avantage legerement les articles lourds et bon marche,
     * mais reste la convention la plus courante ; sans prix saisi, on retombe
     * sur les quantites.
     */
    private Map<Long, BigDecimal> repartirFrais(CommandeFournisseur commande,
                                                Map<Long, BigDecimal> arrivees) {
        Map<Long, BigDecimal> partDeLArrivee = new LinkedHashMap<>();
        // Les frais deja imputes restent acquis : on ne repartit que le solde.
        // Une livraison complementaire ne porte donc rien de plus, sauf si de
        // nouveaux frais ont ete saisis entre-temps.
        BigDecimal dejaReparti = commande.getLignes().stream()
                .map(l -> valeur(l.getQuotePartFrais()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal aRepartir = commande.totalFrais().subtract(dejaReparti);
        if (aRepartir.signum() <= 0) {
            return partDeLArrivee;
        }

        // Sans aucun prix saisi, la quantite sert de prorata.
        boolean surLaValeur = commande.getLignes().stream()
                .anyMatch(l -> valeur(l.getPrixUnitaireDevise()).signum() > 0);
        BigDecimal baseAttendue = BigDecimal.ZERO;
        BigDecimal baseArrivee = BigDecimal.ZERO;
        for (LigneCommandeFournisseur ligne : commande.getLignes()) {
            BigDecimal poids = surLaValeur
                    ? valeur(ligne.getPrixUnitaireDevise()) : BigDecimal.ONE;
            baseAttendue = baseAttendue.add(poids.multiply(reliquat(ligne)));
            baseArrivee = baseArrivee.add(poids.multiply(
                    arrivees.getOrDefault(ligne.getId(), BigDecimal.ZERO)));
        }
        if (baseAttendue.signum() <= 0) {
            return partDeLArrivee;
        }

        BigDecimal attribue = BigDecimal.ZERO;
        Long derniereServie = null;
        for (LigneCommandeFournisseur ligne : commande.getLignes()) {
            BigDecimal arrivee = arrivees.getOrDefault(ligne.getId(), BigDecimal.ZERO);
            if (arrivee.signum() <= 0) {
                continue;
            }
            BigDecimal poids = surLaValeur
                    ? valeur(ligne.getPrixUnitaireDevise()) : BigDecimal.ONE;
            BigDecimal quotePart = aRepartir.multiply(poids.multiply(arrivee))
                    .divide(baseAttendue, 2, RoundingMode.HALF_UP);
            attribue = attribue.add(quotePart);
            derniereServie = ligne.getId();
            partDeLArrivee.put(ligne.getId(), quotePart);
        }

        // La livraison qui solde le dossier emporte le reste, arrondis compris :
        // aucun centime de frais ne doit rester en l'air.
        if (derniereServie != null && baseArrivee.compareTo(baseAttendue) >= 0) {
            BigDecimal reste = aRepartir.subtract(attribue);
            partDeLArrivee.computeIfPresent(derniereServie, (id, part) -> part.add(reste));
        }
        for (LigneCommandeFournisseur ligne : commande.getLignes()) {
            BigDecimal part = partDeLArrivee.get(ligne.getId());
            if (part != null) {
                ligne.setQuotePartFrais(valeur(ligne.getQuotePartFrais()).add(part));
            }
        }
        return partDeLArrivee;
    }

    /**
     * Cout unitaire de la marchandise qui vient d'arriver : sa part de
     * marchandise convertie, plus la quote-part de frais qu'elle vient de
     * recevoir. C'est ce chiffre, et non la moyenne de la ligne, qui pondere le
     * cout moyen du produit.
     */
    private BigDecimal coutDeCetteArrivee(CommandeFournisseur commande,
                                          LigneCommandeFournisseur ligne,
                                          BigDecimal arrivee, BigDecimal fraisDeLArrivee) {
        BigDecimal taux = valeur(commande.getTauxChange(), BigDecimal.ONE);
        BigDecimal marchandise = valeur(ligne.getPrixUnitaireDevise()).multiply(taux)
                .multiply(arrivee);
        return marchandise.add(fraisDeLArrivee).divide(arrivee, 4, RoundingMode.HALF_UP);
    }

    /** Cout moyen de la ligne, toutes receptions confondues. */
    private void recalculerCoutUnitaire(CommandeFournisseur commande) {
        BigDecimal taux = valeur(commande.getTauxChange(), BigDecimal.ONE);
        for (LigneCommandeFournisseur ligne : commande.getLignes()) {
            BigDecimal recue = valeur(ligne.getQuantiteRecue());
            if (recue.signum() <= 0) {
                ligne.setCoutUnitaireMAD(null);
                continue;
            }
            BigDecimal marchandise = valeur(ligne.getPrixUnitaireDevise())
                    .multiply(taux).multiply(recue);
            ligne.setCoutUnitaireMAD(marchandise.add(valeur(ligne.getQuotePartFrais()))
                    .divide(recue, 4, RoundingMode.HALF_UP));
        }
    }

    /**
     * Cout unitaire moyen pondere : le stock deja present garde son cout, la
     * marchandise qui arrive apporte le sien, et les deux se melangent au
     * prorata des quantites.
     *
     * <p>Un stock sans cout connu (entre par saisie manuelle) ne pese pas dans
     * la moyenne : lui preter le cout du nouvel arrivage serait inventer une
     * donnee.
     */
    private void majCoutMoyen(Produit produit, BigDecimal quantiteRecue, BigDecimal coutUnitaire) {
        if (coutUnitaire == null || coutUnitaire.signum() <= 0) {
            return;
        }
        BigDecimal stockAvant = stockProduitRepository.quantiteTotale(produit.getId());
        BigDecimal coutAvant = produit.getCoutRevientMoyen();

        if (coutAvant == null || stockAvant == null || stockAvant.signum() <= 0) {
            produit.setCoutRevientMoyen(coutUnitaire);
        } else {
            BigDecimal valeurAvant = stockAvant.multiply(coutAvant);
            BigDecimal valeurEntree = quantiteRecue.multiply(coutUnitaire);
            produit.setCoutRevientMoyen(valeurAvant.add(valeurEntree)
                    .divide(stockAvant.add(quantiteRecue), 4, RoundingMode.HALF_UP));
        }
        produitRepository.save(produit);
    }

    private BigDecimal valeurLigneRecue(LigneCommandeFournisseur ligne) {
        BigDecimal quantite = ligne.getQuantiteRecue();
        if (quantite == null || quantite.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return valeur(ligne.getPrixUnitaireDevise()).multiply(quantite);
    }

    private long nombreLignesRecues(CommandeFournisseur commande) {
        long nombre = commande.getLignes().stream()
                .filter(l -> l.getQuantiteRecue() != null && l.getQuantiteRecue().signum() > 0)
                .count();
        return nombre > 0 ? nombre : 1;
    }

    private BigDecimal valeur(BigDecimal v) {
        return valeur(v, BigDecimal.ZERO);
    }

    private BigDecimal valeur(BigDecimal v, BigDecimal defaut) {
        return v != null ? v : defaut;
    }

    // --- Saisie ---

    private void appliquer(CommandeFournisseur commande, CommandeFournisseurRequest request) {
        Fournisseur fournisseur = fournisseurRepository.findById(request.fournisseurId())
                .orElseThrow(() -> new ResourceNotFoundException("Fournisseur", request.fournisseurId()));
        Depot depot = depotRepository.findByCode(request.depotReceptionCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Depot inconnu : " + request.depotReceptionCode()));

        commande.setFournisseur(fournisseur);
        commande.setDepotReception(depot);
        commande.setDateArriveePrevue(request.dateArriveePrevue());
        commande.setIncoterm(request.incoterm());
        commande.setModeTransport(request.modeTransport());
        commande.setPaysOrigine(request.paysOrigine());
        commande.setFraisTransportEnDevise(Boolean.TRUE.equals(request.fraisTransportEnDevise()));
        // Date de commande saisissable : un achat passe par telephone la semaine
        // derniere et saisi aujourd'hui ne doit pas porter la date du jour.
        commande.setDateCommande(request.dateCommande());
        commande.setTransporteur(request.transporteur());
        commande.setReferenceTransport(request.referenceTransport());
        commande.setPortArrivee(request.portArrivee());
        commande.setObservations(request.observations());
        commande.setFraisFret(request.fraisFret());
        commande.setFraisAssurance(request.fraisAssurance());
        commande.setDroitsDouane(request.droitsDouane());
        commande.setFraisTransit(request.fraisTransit());
        appliquerDevise(commande, request);

        BigDecimal total = BigDecimal.ZERO;
        for (LigneCommandeFournisseurRequest l : request.lignes()) {
            Produit produit = produitRepository.findById(l.produitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produit", l.produitId()));
            BigDecimal prix = l.prixUnitaireDevise() != null
                    ? l.prixUnitaireDevise() : BigDecimal.ZERO;
            BigDecimal montant = prix.multiply(l.quantiteCommandee());
            commande.ajouterLigne(LigneCommandeFournisseur.builder()
                    .produit(produit)
                    .referenceFournisseur(l.referenceFournisseur() != null
                            ? l.referenceFournisseur()
                            : referenceChez(produit, fournisseur))
                    .designation(produit.getDesignation())
                    .quantiteCommandee(l.quantiteCommandee())
                    .prixUnitaireDevise(prix)
                    .montantDevise(montant)
                    .build());
            total = total.add(montant);
        }
        commande.setMontantDevise(total);
    }

    /**
     * Un achat local se passe de devise : on retient alors le dirham au taux 1,
     * pour que le calcul du cout de revient soit le meme dans les deux cas.
     */
    private void appliquerDevise(CommandeFournisseur commande, CommandeFournisseurRequest request) {
        String devise = request.devise() != null && !request.devise().isBlank()
                ? request.devise().trim().toUpperCase(java.util.Locale.ROOT) : "MAD";
        BigDecimal taux = request.tauxChange();
        if ("MAD".equals(devise)) {
            taux = BigDecimal.ONE;
        } else if (taux == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un achat en " + devise + " demande un taux de change : sans lui, "
                            + "le cout de revient ne peut pas etre calcule");
        }
        commande.setDevise(devise);
        commande.setTauxChange(taux);
    }

    /** Reference du produit dans le catalogue de ce fournisseur, si elle y figure. */
    private String referenceChez(Produit produit, Fournisseur fournisseur) {
        return produit.getFournisseurs().stream()
                .filter(pf -> pf.getFournisseur() != null
                        && pf.getFournisseur().getId().equals(fournisseur.getId()))
                .map(ProduitFournisseur::getReferenceFournisseur)
                .filter(r -> r != null && !r.isBlank())
                .findFirst()
                .orElse(null);
    }

    // --- Garde-fous ---

    private void exigerModifiable(CommandeFournisseur commande) {
        if (!MODIFIABLES.contains(commande.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le bon de commande est deja parti chez le fournisseur : reemettez "
                            + "une commande plutot que de modifier celle-ci");
        }
    }

    private void exigerTransition(CommandeFournisseur commande,
                                  StatutCommandeFournisseur nouveau) {
        if (commande.getStatut() == StatutCommandeFournisseur.ANNULEE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette commande est annulee");
        }
        Set<StatutCommandeFournisseur> suites =
                SUITES.getOrDefault(commande.getStatut(), Set.of());
        if (!suites.contains(nouveau)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Passage impossible de " + commande.getStatut() + " a " + nouveau);
        }
    }

    private CommandeFournisseur getOrThrow(Long id) {
        return commandeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commande fournisseur", id));
    }

    private String genererNumero() {
        String prefix = "CF-" + Year.now().getValue() + "-";
        return NumeroDocument.suivant(prefix, commandeRepository.dernierNumero(prefix));
    }

    // --- Mise en forme ---

    private CommandeFournisseurResponse toResponse(CommandeFournisseur c) {
        Utilisateur acheteur = c.getAcheteur();
        BigDecimal montantMAD = c.getMontantDevise() != null && c.getTauxChange() != null
                ? c.getMontantDevise().multiply(c.getTauxChange())
                        .setScale(2, java.math.RoundingMode.HALF_UP)
                : null;
        List<LigneCommandeFournisseurResponse> lignes = c.getLignes().stream()
                .map(l -> new LigneCommandeFournisseurResponse(
                        l.getId(),
                        l.getProduit() != null ? l.getProduit().getId() : null,
                        l.getProduit() != null ? l.getProduit().getReference() : null,
                        l.getReferenceFournisseur(),
                        l.getDesignation(),
                        l.getQuantiteCommandee(),
                        l.getQuantiteRecue(),
                        l.getPrixUnitaireDevise(),
                        l.getMontantDevise(),
                        l.getQuotePartFrais(),
                        l.getCoutUnitaireMAD()))
                .toList();

        return new CommandeFournisseurResponse(
                c.getId(),
                c.getNumero(),
                c.getStatut(),
                c.getFournisseur() != null ? c.getFournisseur().getId() : null,
                c.getFournisseur() != null ? c.getFournisseur().getNom() : null,
                c.getDepotReception() != null ? c.getDepotReception().getCode() : null,
                acheteur != null ? (acheteur.getPrenom() + " " + acheteur.getNom()).trim() : null,
                c.getDateCreation(),
                c.getDateCommande(),
                c.getDateArriveePrevue(),
                c.getDateTransit(),
                c.getDateDouane(),
                c.getDatePremiereReception(),
                c.getDateReception(),
                c.getDateAnnulation(),
                c.getDevise(),
                c.getTauxChange(),
                c.getIncoterm(),
                c.getModeTransport(),
                c.getPaysOrigine(),
                c.isFraisTransportEnDevise(),
                c.getTransporteur(),
                c.getReferenceTransport(),
                c.getPortArrivee(),
                c.getMontantDevise(),
                montantMAD,
                c.getFraisFret(),
                c.getFraisAssurance(),
                c.getDroitsDouane(),
                c.getFraisTransit(),
                c.totalFrais(),
                montantMAD != null ? montantMAD.add(c.totalFrais()) : null,
                c.getObservations(),
                lignes);
    }
}
