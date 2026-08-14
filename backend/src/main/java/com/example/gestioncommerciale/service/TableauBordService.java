package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.TableauBordResponse;
import com.example.gestioncommerciale.dto.TableauBordResponse.Element;
import com.example.gestioncommerciale.dto.TableauBordResponse.FormeVisuel;
import com.example.gestioncommerciale.dto.TableauBordResponse.FileAttente;
import com.example.gestioncommerciale.dto.TableauBordResponse.Barre;
import com.example.gestioncommerciale.dto.TableauBordResponse.Indicateur;
import com.example.gestioncommerciale.dto.TableauBordResponse.Visuel;
import com.example.gestioncommerciale.entity.Client;
import com.example.gestioncommerciale.entity.Commande;
import com.example.gestioncommerciale.entity.CommandeFournisseur;
import com.example.gestioncommerciale.entity.Devis;
import com.example.gestioncommerciale.entity.Facture;
import com.example.gestioncommerciale.entity.ModePaiement;
import com.example.gestioncommerciale.entity.Paiement;
import com.example.gestioncommerciale.entity.Produit;
import com.example.gestioncommerciale.entity.Role;
import com.example.gestioncommerciale.entity.StatutCommande;
import com.example.gestioncommerciale.entity.StatutCommandeFournisseur;
import com.example.gestioncommerciale.entity.StatutDevis;
import com.example.gestioncommerciale.entity.StatutPaiement;
import com.example.gestioncommerciale.entity.Utilisateur;
import com.example.gestioncommerciale.repository.ClientRepository;
import com.example.gestioncommerciale.repository.CommandeFournisseurRepository;
import com.example.gestioncommerciale.repository.CommandeRepository;
import com.example.gestioncommerciale.repository.DevisRepository;
import com.example.gestioncommerciale.repository.FactureRepository;
import com.example.gestioncommerciale.repository.FournisseurRepository;
import com.example.gestioncommerciale.repository.MarqueRepository;
import com.example.gestioncommerciale.repository.MouvementStockRepository;
import com.example.gestioncommerciale.repository.PaiementRepository;
import com.example.gestioncommerciale.repository.ProduitRepository;
import com.example.gestioncommerciale.repository.UtilisateurRepository;
import com.example.gestioncommerciale.security.CurrentUserService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Compose le tableau de bord de l'utilisateur connecte.
 *
 * <p>Un seul endpoint, six compositions. Le principe est le meme partout :
 * quelques chiffres pour situer, puis des files d'attente qui disent quoi faire
 * et menent au document. Rien n'est stocke : tout se deduit des donnees, donc
 * l'ecran ne peut pas afficher une tache deja traitee.
 *
 * <p>Le commercial ne voit que son portefeuille, par la meme restriction que
 * partout ailleurs : {@link CurrentUserService#restrictionAuCommercial()}.
 */
@Service
public class TableauBordService {

    /** Au-dela, une file cesse d'etre une liste de taches et devient un listing. */
    private static final int TAILLE_FILE = 5;
    /** Un devis qui expire dans plus d'une semaine n'appelle pas encore d'action. */
    private static final int JOURS_AVANT_EXPIRATION = 7;

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MOIS_FR =
            DateTimeFormatter.ofPattern("MMM yy", Locale.FRANCE);

    /** Dossiers partis chez le fournisseur et pas encore entres en stock. */
    private static final Set<StatutCommandeFournisseur> EN_ROUTE = Set.of(
            StatutCommandeFournisseur.COMMANDEE,
            StatutCommandeFournisseur.EN_TRANSIT,
            StatutCommandeFournisseur.EN_DOUANE);

    /**
     * Devis dont le sort est fixe. Un devis encore chez le client n'a pas
     * echoue : il ne doit peser dans aucun taux d'acceptation.
     */
    private static final Set<StatutDevis> STATUTS_TRANCHES =
            Set.of(StatutDevis.ACCEPTE, StatutDevis.REFUSE, StatutDevis.EXPIRE);

    private final DevisRepository devisRepository;
    private final CommandeRepository commandeRepository;
    private final CommandeFournisseurRepository commandeFournisseurRepository;
    private final FactureRepository factureRepository;
    private final PaiementRepository paiementRepository;
    private final ClientRepository clientRepository;
    private final ProduitRepository produitRepository;
    private final MarqueRepository marqueRepository;
    private final FournisseurRepository fournisseurRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final TableauBordStockService tableauBordStockService;
    private final PolitiquePouvoirs politiquePouvoirs;
    private final CurrentUserService currentUserService;
    private final DecimalFormat montantFormat;

    public TableauBordService(DevisRepository devisRepository,
                              CommandeRepository commandeRepository,
                              CommandeFournisseurRepository commandeFournisseurRepository,
                              FactureRepository factureRepository,
                              PaiementRepository paiementRepository,
                              ClientRepository clientRepository,
                              ProduitRepository produitRepository,
                              MarqueRepository marqueRepository,
                              FournisseurRepository fournisseurRepository,
                              UtilisateurRepository utilisateurRepository,
                              MouvementStockRepository mouvementStockRepository,
                              TableauBordStockService tableauBordStockService,
                              PolitiquePouvoirs politiquePouvoirs,
                              CurrentUserService currentUserService) {
        this.devisRepository = devisRepository;
        this.commandeRepository = commandeRepository;
        this.commandeFournisseurRepository = commandeFournisseurRepository;
        this.factureRepository = factureRepository;
        this.paiementRepository = paiementRepository;
        this.clientRepository = clientRepository;
        this.produitRepository = produitRepository;
        this.marqueRepository = marqueRepository;
        this.fournisseurRepository = fournisseurRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.mouvementStockRepository = mouvementStockRepository;
        this.tableauBordStockService = tableauBordStockService;
        this.politiquePouvoirs = politiquePouvoirs;
        this.currentUserService = currentUserService;

        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRANCE);
        symboles.setGroupingSeparator(' ');
        symboles.setDecimalSeparator(',');
        this.montantFormat = new DecimalFormat("#,##0.00", symboles);
    }

    @Transactional(readOnly = true)
    public TableauBordResponse construire() {
        Utilisateur utilisateur = currentUserService.getUtilisateurCourant();
        Role role = utilisateur != null ? utilisateur.getRole() : Role.COMMERCIAL;
        return switch (role) {
            case ADMIN -> admin();
            case RESPONSABLE_COMMERCIAL -> responsableCommercial(utilisateur);
            case COMMERCIAL -> commercial(utilisateur);
            case MAGASINIER -> magasinier();
            case RESPONSABLE_IMPORT -> responsableImport();
            case COMPTABLE -> comptable();
        };
    }

    // --- Un tableau par role ---

    private TableauBordResponse admin() {
        List<Devis> remisesDevis = devisRepository.parStatut(StatutDevis.EN_ATTENTE_VALIDATION, null);
        List<Commande> remisesCommandes =
                commandeRepository.parStatut(StatutCommande.EN_ATTENTE_VALIDATION, null);

        return new TableauBordResponse(Role.ADMIN,
                "Vue d'ensemble",
                "Ce qui bloque, et ou en est l'activite",
                List.of(
                        montant("Facture ce mois", montantFactureDuMois(null), "toutes annulations exclues", "neutre"),
                        montant("Encours client", factureRepository.encours(null), "reste du sur factures non soldees", "attention"),
                        nombre("Commandes en cours", commandesEnCours(null), "de la saisie a la livraison", "neutre"),
                        montant("Valeur du stock", valeurDuStock(), "au prix catalogue", "neutre")),
                filesNonVides(
                        fileDevisEtCommandes("Remises a arbitrer", remisesDevis, remisesCommandes,
                                "Au-dela du seuil autorise : le document est gele tant que personne n'a tranche"),
                        fileFactures("Factures en retard",
                                "Echeance depassee, montant non solde",
                                factureRepository.enRetard(null), "alerte"),
                        fileClients("Clients bloques",
                                "Plafond de credit depasse : ils ne peuvent plus commander",
                                clientRepository.bloques(null)),
                        fileInvitations()),
                visuelChiffreDouzeMois(null));
    }

    private TableauBordResponse responsableCommercial(Utilisateur moi) {
        // Il ne voit que ce qu'il peut effectivement trancher : au-dela de son
        // seuil, la remise remonte a l'administrateur, et la lui presenter ne
        // ferait que l'envoyer se prendre un refus.
        List<Devis> remisesDevis =
                devisRepository.parStatut(StatutDevis.EN_ATTENTE_VALIDATION, null).stream()
                        .filter(d -> !politiquePouvoirs.depassePouvoirDe(moi, d))
                        .toList();
        List<Commande> remisesCommandes =
                commandeRepository.parStatut(StatutCommande.EN_ATTENTE_VALIDATION, null).stream()
                        .filter(c -> !politiquePouvoirs.depassePouvoirDe(moi, c))
                        .toList();

        return new TableauBordResponse(Role.RESPONSABLE_COMMERCIAL,
                "Pilotage commercial",
                "Les arbitrages qui attendent, et l'etat du portefeuille",
                List.of(
                        montant("Facture ce mois", montantFactureDuMois(null), "toutes annulations exclues", "neutre"),
                        tauxAcceptation("Taux d'acceptation", null),
                        montant("Encours client", factureRepository.encours(null), "reste du sur factures non soldees", "attention"),
                        nombre("Envoyes sans reponse", devisRepository.envoyesSansReponse(null).size(),
                                "devis chez le client, en attente", "attention")),
                filesNonVides(
                        fileDevisEtCommandes("Remises a arbitrer", remisesDevis, remisesCommandes,
                                "Dans la limite de votre seuil : au-dela, l'arbitrage revient a l'administrateur"),
                        fileDevis("Devis qui expirent",
                                "Chez le client, validite bientot echue : relancer ou prolonger",
                                devisRepository.expirantAvant(LocalDate.now(),
                                        LocalDate.now().plusDays(JOURS_AVANT_EXPIRATION), null),
                                "attention"),
                        fileClients("Clients bloques",
                                "Plafond de credit depasse : ils ne peuvent plus commander",
                                clientRepository.bloques(null)),
                        fileClientsSansCommercial()),
                visuelComparatifCommerciaux());
    }

    private TableauBordResponse commercial(Utilisateur moi) {
        Long id = moi != null ? moi.getId() : null;
        return new TableauBordResponse(Role.COMMERCIAL,
                "Mon activite",
                "Mon portefeuille et ce qui attend une suite",
                List.of(
                        montant("Mon chiffre du mois", montantFactureDuMois(id), "facture a mes clients", "neutre"),
                        nombre("Mes devis en cours", devisEnCours(id), "brouillons, arbitrages et envois", "neutre"),
                        tauxAcceptation("Mon taux d'acceptation", id),
                        montant("Mon encours", factureRepository.encours(id), "reste du par mes clients", "attention")),
                filesNonVides(
                        fileDevis("Mes remises en attente",
                                "L'encadrement doit trancher : ni envoi ni impression d'ici la",
                                devisRepository.parStatut(StatutDevis.EN_ATTENTE_VALIDATION, id),
                                "attention"),
                        fileDevis("Envoyes, sans reponse",
                                "Le client a recu le devis et n'a pas repondu : a relancer",
                                devisRepository.envoyesSansReponse(id), "neutre"),
                        fileDevis("Devis qui expirent",
                                "Validite bientot echue : relancer ou prolonger",
                                devisRepository.expirantAvant(LocalDate.now(),
                                        LocalDate.now().plusDays(JOURS_AVANT_EXPIRATION), id),
                                "attention"),
                        fileClients("Mes clients bloques",
                                "Plafond depasse : je ne peux plus leur vendre",
                                clientRepository.bloques(id))),
                visuelMesPremiersClients(id));
    }

    private TableauBordResponse magasinier() {
        List<Commande> aValider = commandeRepository.parStatut(StatutCommande.EN_ATTENTE, null);
        List<Commande> aPreparer = commandeRepository.parStatut(StatutCommande.VALIDEE, null);
        List<Commande> aLivrer = commandeRepository.parStatut(StatutCommande.EN_PREPARATION, null);
        var stock = tableauBordStockService.construire(90);
        BigDecimal reservees = stock.parDepot().stream()
                .map(d -> valeur(d.quantiteReservee()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TableauBordResponse(Role.MAGASINIER,
                "Entrepot",
                "Le travail du jour, dans l'ordre du cycle",
                List.of(
                        nombre("Ruptures", stock.compteurs().ruptures(),
                                "dont " + stock.compteurs().toutReserve() + " entierement reserves", "alerte"),
                        new Indicateur("Quantites reservees", formatQuantite(reservees),
                                "promises a des commandes validees", "attention"),
                        nombre("Mouvements du jour",
                                mouvementStockRepository.countByDateMouvementAfter(
                                        LocalDate.now().atStartOfDay()),
                                "entrees, sorties et ajustements", "neutre"),
                        nombre("Depots", stock.compteurs().depots(), "en service", "neutre")),
                filesNonVides(
                        fileCommandes("Commandes a valider",
                                "Choisir les depots de prelevement : c'est ce qui reserve le stock",
                                aValider, "neutre"),
                        fileCommandes("Commandes a preparer",
                                "Stock deja reserve, il reste a rassembler la marchandise",
                                aPreparer, "attention"),
                        fileCommandes("Commandes a livrer",
                                "Preparees : la livraison sortira le stock physiquement",
                                aLivrer, "succes")),
                visuelRupturesParDepot());
    }

    private TableauBordResponse responsableImport() {
        List<Produit> sansFiche = produitRepository.sansFicheTechnique();
        List<Produit> sansFournisseur = produitRepository.sansFournisseur();
        List<Produit> sansPrix = produitRepository.sansPrix();
        int ruptures = tableauBordStockService.construire(90).compteurs().ruptures();

        List<CommandeFournisseur> enRetard =
                commandeFournisseurRepository.enRetardArrivee(LocalDate.now());
        List<CommandeFournisseur> brouillons = commandeFournisseurRepository
                .parStatuts(List.of(StatutCommandeFournisseur.BROUILLON));
        List<CommandeFournisseur> enRoute = commandeFournisseurRepository.parStatuts(EN_ROUTE);
        List<CommandeFournisseur> aReceptionner = commandeFournisseurRepository
                .parStatuts(List.of(StatutCommandeFournisseur.EN_DOUANE));
        BigDecimal engage = commandeFournisseurRepository.montantEngage(EN_ROUTE);

        return new TableauBordResponse(Role.RESPONSABLE_IMPORT,
                "Achats et catalogue",
                "Les dossiers en route, et ce qui manque sur les fiches",
                List.of(
                        nombre("Commandes en route", enRoute.size(),
                                "emises, pas encore receptionnees", "neutre"),
                        montant("Engage aupres des fournisseurs", engage,
                                "marchandise commandee, non encore recue", "attention"),
                        nombre("References", produitRepository.count(), "au catalogue", "neutre"),
                        nombre("Produits en rupture", ruptures, "plus rien de vendable", "alerte")),
                filesNonVides(
                        fileCommandesFournisseur("Arrivees en retard",
                                "La date annoncee est passee : le fournisseur ne previendra pas",
                                enRetard, "alerte"),
                        fileCommandesFournisseur("A receptionner",
                                "Dedouanees : la marchandise attend son entree en stock",
                                aReceptionner, "attention"),
                        fileCommandesFournisseur("En route",
                                "Emises, en transit ou en douane", enRoute, "neutre"),
                        fileCommandesFournisseur("Brouillons a emettre",
                                "Preparees mais jamais envoyees au fournisseur",
                                brouillons, "attention"),
                        fileProduits("Sans fiche technique",
                                "Rien a remettre au client pour ces references", sansFiche, "attention"),
                        fileProduits("Sans fournisseur",
                                "Impossible de savoir chez qui les commander", sansFournisseur, "alerte"),
                        fileProduits("Sans prix",
                                "Invendables en l'etat : aucun montant ne peut etre calcule",
                                sansPrix, "alerte"),
                        fileProduits("Jamais entrees en stock",
                                "Au catalogue, mais l'entrepot ne les a jamais vues",
                                produitRepository.jamaisEntresEnStock(), "neutre")),
                visuelProduitsQuiSortent());
    }

    private TableauBordResponse comptable() {
        List<Commande> aFacturer = commandeRepository.livreesNonFacturees();
        List<Facture> enRetard = factureRepository.enRetard(null);
        BigDecimal montantEnRetard = enRetard.stream()
                .map(f -> valeur(f.getMontantTTC()).subtract(valeur(f.getMontantPaye())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Paiement> effets = paiementRepository.effetsEnAttente();
        BigDecimal totalEffets = effets.stream()
                .map(p -> valeur(p.getMontant()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TableauBordResponse(Role.COMPTABLE,
                "Facturation",
                "Ce qui reste a facturer, a envoyer et a encaisser",
                List.of(
                        montant("Facture ce mois", montantFactureDuMois(null), "toutes annulations exclues", "neutre"),
                        montant("Encaisse ce mois", paiementRepository.encaisseDepuis(debutDuMois()),
                                "reellement rentre en caisse", "succes"),
                        montant("Effets a encaisser", totalEffets,
                                effets.size() + " cheque(s) et traite(s) en portefeuille", "attention"),
                        montant("Retard total", montantEnRetard,
                                enRetard.size() + " facture(s) echue(s)", "alerte")),
                filesNonVides(
                        fileEffets(effets),
                        fileCommandes("Livrees, non facturees",
                                "La marchandise est partie sans que rien n'ait ete demande au client",
                                aFacturer, "alerte"),
                        fileFactures("Factures en retard",
                                "Echeance depassee, montant non solde", enRetard, "alerte"),
                        fileFactures("Jamais transmises au client",
                                "Emises mais non envoyees : elles ne seront pas payees",
                                factureRepository.jamaisEnvoyees(), "attention")),
                visuelBalanceAgee());
    }

    // --- Visuels ---

    /** Serie mensuelle sur douze mois glissants, mois courant compris. */
    private Visuel visuelChiffreDouzeMois(Long commercialId) {
        YearMonth debut = YearMonth.now().minusMonths(11);
        Map<YearMonth, BigDecimal> parMois = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            parMois.put(debut.plusMonths(i), BigDecimal.ZERO);
        }
        for (Object[] ligne : factureRepository.montantsDepuis(
                debut.atDay(1).atStartOfDay(), commercialId)) {
            YearMonth mois = YearMonth.from((LocalDateTime) ligne[0]);
            parMois.computeIfPresent(mois, (m, cumul) -> cumul.add(valeur((BigDecimal) ligne[1])));
        }
        List<Barre> barres = parMois.entrySet().stream()
                .map(e -> new Barre(e.getKey().format(MOIS_FR), formatMontant(e.getValue()) + " DH",
                        "", 0))
                .toList();
        return new Visuel("Chiffre facture sur 12 mois",
                "Montants TTC, annulations exclues",
                FormeVisuel.SERIE,
                avecParts(barres, parMois.values()));
    }

    /**
     * Chaque commercial avec son chiffre et son taux d'acceptation : deux
     * mesures qui ne disent pas la meme chose, un gros chiffre pouvant venir de
     * peu d'affaires bien negociees.
     */
    private Visuel visuelComparatifCommerciaux() {
        Map<Long, BigDecimal> chiffres = new LinkedHashMap<>();
        for (Object[] ligne : factureRepository.chiffreParCommercial()) {
            chiffres.put((Long) ligne[0], valeur((BigDecimal) ligne[1]));
        }
        Map<Long, long[]> sorts = new LinkedHashMap<>();
        for (Object[] ligne : devisRepository.comptesParCommercialEtStatut()) {
            long[] compteur = sorts.computeIfAbsent((Long) ligne[0], id -> new long[]{0, 0});
            StatutDevis statut = (StatutDevis) ligne[1];
            long nombre = (Long) ligne[2];
            if (statut == StatutDevis.ACCEPTE) {
                compteur[0] += nombre;
            }
            if (STATUTS_TRANCHES.contains(statut)) {
                compteur[1] += nombre;
            }
        }

        List<Utilisateur> vendeurs = utilisateurRepository.findAll(Sort.by("nom")).stream()
                .filter(u -> u.getRole() == Role.COMMERCIAL
                        || u.getRole() == Role.RESPONSABLE_COMMERCIAL)
                .toList();
        List<BigDecimal> valeurs = new ArrayList<>();
        List<Barre> barres = new ArrayList<>();
        for (Utilisateur v : vendeurs) {
            BigDecimal chiffre = chiffres.getOrDefault(v.getId(), BigDecimal.ZERO);
            long[] compteur = sorts.getOrDefault(v.getId(), new long[]{0, 0});
            valeurs.add(chiffre);
            barres.add(new Barre(
                    (v.getPrenom() + " " + v.getNom()).trim(),
                    formatMontant(chiffre) + " DH",
                    compteur[1] == 0 ? "aucun devis tranche"
                            : compteur[0] + "/" + compteur[1] + " devis acceptes",
                    0));
        }
        return new Visuel("Comparatif par commercial",
                "Chiffre facture a leurs clients, et sort de leurs devis",
                FormeVisuel.CLASSEMENT,
                avecParts(barres, valeurs));
    }

    private Visuel visuelMesPremiersClients(Long commercialId) {
        List<Object[]> lignes = factureRepository.chiffreParClient(commercialId).stream()
                .limit(5).toList();
        List<BigDecimal> valeurs = lignes.stream()
                .map(l -> valeur((BigDecimal) l[1])).toList();
        List<Barre> barres = lignes.stream()
                .map(l -> new Barre((String) l[0],
                        formatMontant(valeur((BigDecimal) l[1])) + " DH", "", 0))
                .toList();
        return new Visuel("Mes cinq premiers clients",
                "Chiffre facture depuis l'origine",
                FormeVisuel.CLASSEMENT,
                avecParts(barres, valeurs));
    }

    private Visuel visuelRupturesParDepot() {
        Map<String, List<String>> parDepot = tableauBordStockService.rupturesParDepot();
        List<BigDecimal> valeurs = parDepot.values().stream()
                .map(refs -> BigDecimal.valueOf(refs.size())).toList();
        List<Barre> barres = parDepot.entrySet().stream()
                .map(e -> new Barre("Depot " + e.getKey(),
                        e.getValue().size() + " rupture(s)",
                        String.join(", ", e.getValue().stream().limit(5).toList()), 0))
                .toList();
        return new Visuel("Ruptures par depot",
                "Absent d'un seul depot, cela se transfere ; absent des deux, cela se commande",
                FormeVisuel.CLASSEMENT,
                avecParts(barres, valeurs));
    }

    private Visuel visuelProduitsQuiSortent() {
        var sorties = mouvementStockRepository
                .sortiesParProduitDepuis(LocalDateTime.now().minusDays(90)).stream()
                .limit(8).toList();
        List<BigDecimal> valeurs = sorties.stream()
                .map(s -> valeur(s.quantiteSortie())).toList();
        List<Barre> barres = sorties.stream()
                .map(s -> new Barre(s.reference(), formatQuantite(s.quantiteSortie()),
                        s.designation(), 0))
                .toList();
        return new Visuel("Ce qui sort le plus",
                "Sur 90 jours : de quoi anticiper les commandes fournisseur",
                FormeVisuel.CLASSEMENT,
                avecParts(barres, valeurs));
    }

    /** Balance agee : ce qui est du, range par anciennete du retard. */
    private Visuel visuelBalanceAgee() {
        String[] libelles = {"Non echu", "1 a 30 jours", "31 a 60 jours",
                "61 a 90 jours", "Plus de 90 jours"};
        BigDecimal[] tranches = new BigDecimal[libelles.length];
        java.util.Arrays.fill(tranches, BigDecimal.ZERO);

        for (Object[] ligne : factureRepository.restesDusParEcheance()) {
            LocalDate echeance = (LocalDate) ligne[0];
            BigDecimal reste = valeur((BigDecimal) ligne[1]);
            long retard = echeance == null ? 0
                    : ChronoUnit.DAYS.between(echeance, LocalDate.now());
            int tranche = retard <= 0 ? 0 : retard <= 30 ? 1 : retard <= 60 ? 2 : retard <= 90 ? 3 : 4;
            tranches[tranche] = tranches[tranche].add(reste);
        }

        List<BigDecimal> valeurs = List.of(tranches);
        List<Barre> barres = new ArrayList<>();
        for (int i = 0; i < libelles.length; i++) {
            barres.add(new Barre(libelles[i], formatMontant(tranches[i]) + " DH", "", 0));
        }
        return new Visuel("Balance agee",
                "Reste du des factures non soldees, par anciennete du retard",
                FormeVisuel.REPARTITION,
                avecParts(barres, valeurs));
    }

    /**
     * Recalcule les longueurs relatives par rapport a la plus grande valeur.
     * Rapporter au total ecraserait toutes les barres des qu'une seule domine.
     */
    private List<Barre> avecParts(List<Barre> barres, Collection<BigDecimal> valeurs) {
        BigDecimal maximum = valeurs.stream()
                .map(this::valeur)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        if (maximum.signum() <= 0) {
            return barres;
        }
        List<BigDecimal> liste = new ArrayList<>(valeurs);
        List<Barre> resultat = new ArrayList<>();
        for (int i = 0; i < barres.size(); i++) {
            Barre b = barres.get(i);
            double part = i < liste.size()
                    ? valeur(liste.get(i)).divide(maximum, 4, RoundingMode.HALF_UP).doubleValue() * 100
                    : 0;
            resultat.add(new Barre(b.libelle(), b.valeur(), b.detail(), part));
        }
        return resultat;
    }

    // --- Files ---

    private FileAttente fileDevis(String titre, String description, List<Devis> devis, String ton) {
        return file(titre, description, "/devis", devis, d -> new Element(
                d.getNumero(),
                nomClient(d.getClient()),
                formatMontant(d.getMontantTTC()) + " TTC",
                "/devis",
                ton));
    }

    /** Les remises melangent devis et commandes : c'est le meme arbitrage. */
    private FileAttente fileDevisEtCommandes(String titre, List<Devis> devis,
                                             List<Commande> commandes, String description) {
        List<Element> elements = new ArrayList<>();
        for (Devis d : devis) {
            elements.add(new Element(d.getNumero(), nomClient(d.getClient()),
                    formatMontant(d.getMontantTTC()) + " TTC", "/devis", "attention"));
        }
        for (Commande c : commandes) {
            elements.add(new Element(c.getNumero(), nomClient(c.getClient()),
                    formatMontant(c.getMontantTTC()) + " TTC", "/commandes", "attention"));
        }
        int total = elements.size();
        return new FileAttente(titre, description, "/devis", total,
                elements.stream().limit(TAILLE_FILE).toList());
    }

    private FileAttente fileCommandes(String titre, String description,
                                      List<Commande> commandes, String ton) {
        return file(titre, description, "/commandes", commandes, c -> new Element(
                c.getNumero(),
                nomClient(c.getClient()),
                formatMontant(c.getMontantTTC()) + " TTC",
                "/commandes",
                ton));
    }

    private FileAttente fileFactures(String titre, String description,
                                     List<Facture> factures, String ton) {
        return file(titre, description, "/factures", factures, f -> new Element(
                f.getNumero(),
                nomClient(f.getClient()),
                detailFacture(f),
                "/factures",
                ton));
    }

    /**
     * Portefeuille d'effets : ce qui dort dans le tiroir ou attend en banque.
     * L'echeance est ce qui compte — c'est elle qui dit quand l'argent tombe.
     */
    private FileAttente fileEffets(List<Paiement> effets) {
        return file("Effets a encaisser",
                "Cheques et traites recus : l'argent n'est pas encore en banque",
                "/factures", effets, p -> new Element(
                        libelleEffet(p),
                        p.getFacture() != null && p.getFacture().getClient() != null
                                ? p.getFacture().getClient().getNom() : "",
                        formatMontant(p.getMontant()) + " DH — " + echeanceEffet(p),
                        "/factures",
                        p.getStatut() == StatutPaiement.DEPOSE ? "neutre" : "attention"));
    }

    private String libelleEffet(Paiement p) {
        String type = p.getModePaiement() == ModePaiement.TRAITE ? "Traite" : "Cheque";
        return p.getNumeroEffet() != null && !p.getNumeroEffet().isBlank()
                ? type + " " + p.getNumeroEffet() : type;
    }

    private String echeanceEffet(Paiement p) {
        if (p.getDateEcheance() == null) {
            return p.getStatut() == StatutPaiement.DEPOSE ? "remis en banque" : "sans echeance";
        }
        long jours = ChronoUnit.DAYS.between(LocalDate.now(), p.getDateEcheance());
        if (jours < 0) {
            return "echu depuis " + (-jours) + " j";
        }
        return jours == 0 ? "echoit aujourd'hui" : "echoit dans " + jours + " j";
    }

    private FileAttente fileClients(String titre, String description, List<Client> clients) {
        return file(titre, description, "/clients", clients, c -> new Element(
                c.getNom(),
                c.getEmail(),
                "plafond " + formatMontant(c.getPlafondCredit()) + " DH",
                "/clients/" + c.getId(),
                "alerte"));
    }

    private FileAttente fileClientsSansCommercial() {
        return file("Clients sans commercial",
                "Personne ne les suit, et un commercial ne les verrait pas",
                "/clients", clientRepository.sansCommercial(), c -> new Element(
                        c.getNom(), c.getEmail(), "a attribuer", "/clients/" + c.getId(), "attention"));
    }

    /**
     * Une commande fournisseur se lit par son fournisseur et son arrivee : c'est
     * la date attendue qui dit s'il faut s'en inquieter.
     */
    private FileAttente fileCommandesFournisseur(String titre, String description,
                                                 List<CommandeFournisseur> commandes, String ton) {
        return file(titre, description, "/commandes-fournisseur", commandes, c -> new Element(
                c.getNumero(),
                c.getFournisseur() != null ? c.getFournisseur().getNom() : "",
                arrivee(c),
                "/commandes-fournisseur",
                ton));
    }

    /** Date d'arrivee annoncee, avec l'anciennete du retard quand elle est passee. */
    private String arrivee(CommandeFournisseur commande) {
        LocalDate prevue = commande.getDateArriveePrevue();
        if (prevue == null) {
            return "arrivee non datee";
        }
        long retard = ChronoUnit.DAYS.between(prevue, LocalDate.now());
        return retard > 0
                ? "attendue le " + prevue.format(DATE_FR) + " — " + retard + " j de retard"
                : "attendue le " + prevue.format(DATE_FR);
    }

    private FileAttente fileProduits(String titre, String description,
                                     List<Produit> produits, String ton) {
        return file(titre, description, "/produits", produits, p -> new Element(
                p.getReference(),
                p.getDesignation(),
                formatMontant(p.getPrixUnitaireHT()) + " DH HT",
                "/produits/" + p.getId(),
                ton));
    }

    private FileAttente fileInvitations() {
        List<Utilisateur> invites = utilisateurRepository
                .findByActifFalseAndTokenInvitationIsNotNull(Sort.by("nom"));
        return file("Invitations en attente",
                "Comptes crees dont le titulaire n'a pas encore choisi son mot de passe",
                "/utilisateurs", invites, u -> new Element(
                        (u.getPrenom() + " " + u.getNom()).trim(),
                        u.getEmail(),
                        expiration(u),
                        "/utilisateurs",
                        "neutre"));
    }

    private <T> FileAttente file(String titre, String description, String lien,
                                 List<T> source, Function<T, Element> versElement) {
        return new FileAttente(titre, description, lien, source.size(),
                source.stream().limit(TAILLE_FILE).map(versElement).toList());
    }

    /** Une file vide n'a rien a dire : elle encombrerait l'ecran pour rien. */
    private List<FileAttente> filesNonVides(FileAttente... files) {
        return List.of(files).stream().filter(f -> f.total() > 0).toList();
    }

    // --- Chiffres ---

    private BigDecimal montantFactureDuMois(Long commercialId) {
        return factureRepository.montantFactureDepuis(debutDuMois(), commercialId);
    }

    private long devisEnCours(Long commercialId) {
        return devisRepository.compterParStatuts(List.of(
                StatutDevis.BROUILLON, StatutDevis.EN_ATTENTE_VALIDATION, StatutDevis.ENVOYE),
                commercialId);
    }

    private long commandesEnCours(Long commercialId) {
        return commandeRepository.compterParStatuts(List.of(
                StatutCommande.EN_ATTENTE_VALIDATION, StatutCommande.EN_ATTENTE,
                StatutCommande.VALIDEE, StatutCommande.EN_PREPARATION), commercialId);
    }

    private BigDecimal valeurDuStock() {
        return tableauBordStockService.construire(90).valeur().totale();
    }

    private LocalDateTime debutDuMois() {
        return LocalDate.now().withDayOfMonth(1).atStartOfDay();
    }

    // --- Mise en forme ---

    private Indicateur montant(String libelle, BigDecimal valeur, String detail, String ton) {
        return new Indicateur(libelle, formatMontant(valeur) + " DH", detail, ton);
    }

    private Indicateur nombre(String libelle, long valeur, String detail, String ton) {
        return new Indicateur(libelle, String.valueOf(valeur), detail, ton);
    }

    /**
     * Part des devis acceptes parmi ceux qui ont recu un sort definitif. Ceux
     * encore chez le client ne comptent pas : les inclure ferait baisser le taux
     * a chaque envoi, alors que rien n'est encore perdu.
     */
    private Indicateur tauxAcceptation(String libelle, Long commercialId) {
        long acceptes = 0;
        long tranches = 0;
        for (Object[] ligne : devisRepository.comptesParStatut(commercialId)) {
            StatutDevis statut = (StatutDevis) ligne[0];
            long nombre = (Long) ligne[1];
            if (statut == StatutDevis.ACCEPTE) {
                acceptes += nombre;
            }
            if (STATUTS_TRANCHES.contains(statut)) {
                tranches += nombre;
            }
        }
        if (tranches == 0) {
            return new Indicateur(libelle, "—", "aucun devis tranche a ce jour", "neutre");
        }
        long taux = Math.round(acceptes * 100.0 / tranches);
        return new Indicateur(libelle, taux + " %",
                acceptes + " acceptes sur " + tranches + " tranches",
                taux >= 50 ? "succes" : "attention");
    }

    private String formatQuantite(BigDecimal v) {
        return valeur(v).stripTrailingZeros().toPlainString();
    }

    private String detailFacture(Facture f) {
        BigDecimal reste = valeur(f.getMontantTTC()).subtract(valeur(f.getMontantPaye()));
        String echeance = f.getDateEcheance() != null
                ? " — echeance " + f.getDateEcheance().format(DATE_FR) : "";
        return "reste " + formatMontant(reste) + " DH" + echeance;
    }

    private String expiration(Utilisateur u) {
        if (u.getInvitationExpireLe() == null) {
            return "lien expire";
        }
        long jours = ChronoUnit.DAYS.between(LocalDate.now(),
                u.getInvitationExpireLe().toLocalDate());
        return jours < 0 ? "lien expire" : "lien valable " + jours + " j";
    }

    private String nomClient(Client client) {
        return client != null ? client.getNom() : "";
    }

    private String formatMontant(BigDecimal v) {
        return montantFormat.format(valeur(v));
    }

    private BigDecimal valeur(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
