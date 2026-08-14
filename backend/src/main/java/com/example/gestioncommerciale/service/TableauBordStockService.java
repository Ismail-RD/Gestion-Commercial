package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.TableauBordStockResponse;
import com.example.gestioncommerciale.dto.TableauBordStockResponse.Compteurs;
import com.example.gestioncommerciale.dto.TableauBordStockResponse.Flux;
import com.example.gestioncommerciale.dto.TableauBordStockResponse.LigneAjustement;
import com.example.gestioncommerciale.dto.TableauBordStockResponse.LigneDormante;
import com.example.gestioncommerciale.dto.TableauBordStockResponse.LigneManquante;
import com.example.gestioncommerciale.dto.TableauBordStockResponse.LigneRotation;
import com.example.gestioncommerciale.dto.TableauBordStockResponse.TransfertPossible;
import com.example.gestioncommerciale.dto.TableauBordStockResponse.Valeur;
import com.example.gestioncommerciale.dto.TableauBordStockResponse.ValeurParCategorie;
import com.example.gestioncommerciale.dto.TableauBordStockResponse.ValeurParDepot;
import com.example.gestioncommerciale.entity.MouvementStock;
import com.example.gestioncommerciale.entity.TypeMouvement;
import com.example.gestioncommerciale.repository.DepotRepository;
import com.example.gestioncommerciale.repository.MouvementStockRepository;
import com.example.gestioncommerciale.repository.MouvementStockRepository.SortieProduit;
import com.example.gestioncommerciale.repository.MouvementStockRepository.VolumeParType;
import com.example.gestioncommerciale.repository.ProduitRepository;
import com.example.gestioncommerciale.repository.StockProduitRepository;
import com.example.gestioncommerciale.repository.StockProduitRepository.Photo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tableau de bord du stock : une lecture d'ensemble destinee a decider.
 *
 * <p>Rien n'est stocke ni parametre. Tout se deduit de la photo du stock et de
 * l'historique des mouvements, ce qui garantit que l'ecran ne ment jamais et
 * qu'il fonctionne des le premier jour, sans reglage prealable.
 *
 * <p>Le stock est agrege en memoire a partir d'une seule projection : le nombre
 * de lignes est borne par le catalogue multiplie par le nombre de depots, et les
 * croisements demandes (rupture d'un cote, disponible de l'autre) tournent mal
 * en SQL pour un gain nul a cette echelle.
 */
@Service
public class TableauBordStockService {

    /** Au-dela, les listes deviennent illisibles : le tableau de bord n'est pas un export. */
    private static final int TAILLE_LISTE = 10;

    private final StockProduitRepository stockProduitRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final ProduitRepository produitRepository;
    private final DepotRepository depotRepository;

    public TableauBordStockService(StockProduitRepository stockProduitRepository,
                                   MouvementStockRepository mouvementStockRepository,
                                   ProduitRepository produitRepository,
                                   DepotRepository depotRepository) {
        this.stockProduitRepository = stockProduitRepository;
        this.mouvementStockRepository = mouvementStockRepository;
        this.produitRepository = produitRepository;
        this.depotRepository = depotRepository;
    }

    @Transactional(readOnly = true)
    public TableauBordStockResponse construire(int jours) {
        LocalDateTime depuis = LocalDateTime.now().minusDays(jours);
        List<Photo> photo = stockProduitRepository.photoDuStock();

        Map<Long, Agregat> parProduit = agregerParProduit(photo);
        Map<Long, LocalDateTime> derniereSortie = derniereSortieParProduit();

        return new TableauBordStockResponse(
                jours,
                valeur(photo),
                valeurParDepot(photo),
                valeurParCategorie(photo),
                compteurs(parProduit, derniereSortie, depuis),
                ruptures(parProduit),
                transferts(photo),
                dormants(parProduit, derniereSortie, depuis),
                rotations(depuis),
                flux(depuis),
                ajustements(depuis));
    }

    /**
     * Ruptures ventilees par depot, references a l'appui.
     *
     * <p>La liste agregee du tableau de bord dit ce qui manque ; celle-ci dit ou
     * cela manque, ce qui n'est pas la meme decision : un produit absent d'un
     * seul depot se transfere, absent des deux il se commande.
     */
    @Transactional(readOnly = true)
    public Map<String, List<String>> rupturesParDepot() {
        Map<String, List<String>> parDepot = new LinkedHashMap<>();
        for (Photo ligne : stockProduitRepository.photoDuStock()) {
            if (disponible(ligne).signum() <= 0) {
                parDepot.computeIfAbsent(ligne.depotCode(), d -> new ArrayList<>())
                        .add(ligne.reference());
            }
        }
        return parDepot;
    }

    // --- Valeur immobilisee ---

    private Valeur valeur(List<Photo> photo) {
        BigDecimal totale = BigDecimal.ZERO;
        BigDecimal reservee = BigDecimal.ZERO;
        BigDecimal auCout = BigDecimal.ZERO;
        BigDecimal venteChiffree = BigDecimal.ZERO;
        // Un produit sans cout connu compte une fois, quel que soit le nombre
        // de depots ou il se trouve.
        Set<Long> sansCout = new HashSet<>();

        for (Photo l : photo) {
            totale = totale.add(montant(l.quantite(), l.prixUnitaireHT()));
            reservee = reservee.add(montant(l.quantiteReservee(), l.prixUnitaireHT()));
            if (l.coutRevientMoyen() != null && l.coutRevientMoyen().signum() > 0) {
                auCout = auCout.add(montant(l.quantite(), l.coutRevientMoyen()));
                venteChiffree = venteChiffree.add(montant(l.quantite(), l.prixUnitaireHT()));
            } else if (valeur(l.quantite()).signum() > 0) {
                sansCout.add(l.produitId());
            }
        }
        return new Valeur(totale, reservee, totale.subtract(reservee),
                auCout.setScale(2, RoundingMode.HALF_UP),
                venteChiffree, sansCout.size());
    }

    private List<ValeurParDepot> valeurParDepot(List<Photo> photo) {
        Map<String, BigDecimal[]> parDepot = new LinkedHashMap<>();
        for (Photo l : photo) {
            BigDecimal[] cumul = parDepot.computeIfAbsent(l.depotCode(),
                    d -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            cumul[0] = cumul[0].add(montant(l.quantite(), l.prixUnitaireHT()));
            cumul[1] = cumul[1].add(valeur(l.quantite()));
            cumul[2] = cumul[2].add(valeur(l.quantiteReservee()));
        }
        return parDepot.entrySet().stream()
                .map(e -> new ValeurParDepot(e.getKey(), e.getValue()[0],
                        e.getValue()[1], e.getValue()[2]))
                .sorted(Comparator.comparing(ValeurParDepot::depotCode))
                .toList();
    }

    private List<ValeurParCategorie> valeurParCategorie(List<Photo> photo) {
        Map<String, BigDecimal> parCategorie = new HashMap<>();
        for (Photo l : photo) {
            String categorie = l.categorie() != null ? l.categorie() : "Sans categorie";
            parCategorie.merge(categorie, montant(l.quantite(), l.prixUnitaireHT()), BigDecimal::add);
        }
        return parCategorie.entrySet().stream()
                .map(e -> new ValeurParCategorie(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(ValeurParCategorie::valeur).reversed())
                .toList();
    }

    // --- Ce qui manque ---

    private List<LigneManquante> ruptures(Map<Long, Agregat> parProduit) {
        return parProduit.values().stream()
                .filter(a -> a.disponible().signum() <= 0)
                .sorted(Comparator.comparing((Agregat a) -> a.quantite.signum() > 0).reversed()
                        .thenComparing(a -> a.reference))
                .limit(TAILLE_LISTE)
                .map(a -> new LigneManquante(a.produitId, a.reference, a.designation,
                        a.quantite, a.quantiteReservee, a.disponible(),
                        a.quantite.signum() > 0))
                .toList();
    }

    /**
     * Un depot a sec pendant qu'un autre a de quoi servir. C'est la decision la
     * plus immediate qu'offre cet ecran : elle ne demande ni achat, ni delai.
     */
    private List<TransfertPossible> transferts(List<Photo> photo) {
        Map<Long, List<Photo>> parProduit = new LinkedHashMap<>();
        for (Photo l : photo) {
            parProduit.computeIfAbsent(l.produitId(), p -> new ArrayList<>()).add(l);
        }
        List<TransfertPossible> resultat = new ArrayList<>();
        for (List<Photo> lignes : parProduit.values()) {
            Photo mieuxServi = lignes.stream()
                    .max(Comparator.comparing(this::disponible))
                    .orElse(null);
            if (mieuxServi == null || disponible(mieuxServi).signum() <= 0) {
                continue;
            }
            for (Photo ligne : lignes) {
                if (disponible(ligne).signum() <= 0 && !ligne.depotCode().equals(mieuxServi.depotCode())) {
                    resultat.add(new TransfertPossible(
                            ligne.produitId(), ligne.reference(), ligne.designation(),
                            ligne.depotCode(), mieuxServi.depotCode(), disponible(mieuxServi)));
                }
            }
        }
        return resultat.stream()
                .sorted(Comparator.comparing(TransfertPossible::reference))
                .limit(TAILLE_LISTE)
                .toList();
    }

    // --- Ce qui ne bouge plus ---

    private List<LigneDormante> dormants(Map<Long, Agregat> parProduit,
                                         Map<Long, LocalDateTime> derniereSortie,
                                         LocalDateTime depuis) {
        return parProduit.values().stream()
                .filter(a -> a.quantite.signum() > 0)
                .filter(a -> estDormant(a.produitId, derniereSortie, depuis))
                .sorted(Comparator.comparing((Agregat a) -> a.valeur).reversed())
                .limit(TAILLE_LISTE)
                .map(a -> new LigneDormante(a.produitId, a.reference, a.designation,
                        a.quantite, a.valeur, joursDepuis(derniereSortie.get(a.produitId))))
                .toList();
    }

    private boolean estDormant(Long produitId, Map<Long, LocalDateTime> derniereSortie,
                               LocalDateTime depuis) {
        LocalDateTime derniere = derniereSortie.get(produitId);
        return derniere == null || derniere.isBefore(depuis);
    }

    private Long joursDepuis(LocalDateTime date) {
        return date == null ? null : Duration.between(date, LocalDateTime.now()).toDays();
    }

    // --- Mouvements ---

    private List<LigneRotation> rotations(LocalDateTime depuis) {
        List<SortieProduit> sorties = mouvementStockRepository.sortiesParProduitDepuis(depuis);
        return sorties.stream()
                .limit(TAILLE_LISTE)
                .map(s -> new LigneRotation(s.produitId(), s.reference(), s.designation(),
                        s.quantiteSortie()))
                .toList();
    }

    private Flux flux(LocalDateTime depuis) {
        BigDecimal entrees = BigDecimal.ZERO;
        BigDecimal sorties = BigDecimal.ZERO;
        BigDecimal ajustements = BigDecimal.ZERO;
        int nombre = 0;
        for (VolumeParType volume : mouvementStockRepository.volumesDepuis(depuis)) {
            BigDecimal quantite = valeur(volume.quantite());
            nombre += (int) volume.nombre();
            if (volume.type() == TypeMouvement.ENTREE) {
                entrees = entrees.add(quantite);
            } else if (volume.type() == TypeMouvement.SORTIE) {
                // Une sortie est enregistree en negatif : on l'affiche en volume.
                sorties = sorties.add(quantite.abs());
            } else {
                // Les ajustements gardent leur signe : la correction nette dit
                // si l'inventaire a trouve plus ou moins que prevu.
                ajustements = ajustements.add(quantite);
            }
        }
        return new Flux(entrees, sorties, ajustements, nombre);
    }

    private List<LigneAjustement> ajustements(LocalDateTime depuis) {
        return mouvementStockRepository.ajustementsDepuis(depuis).stream()
                .limit(TAILLE_LISTE)
                .map(this::toLigneAjustement)
                .toList();
    }

    private LigneAjustement toLigneAjustement(MouvementStock m) {
        return new LigneAjustement(
                m.getDateMouvement(),
                m.getProduit() != null ? m.getProduit().getReference() : "",
                m.getProduit() != null ? m.getProduit().getDesignation() : "",
                m.getDepot() != null ? m.getDepot().getCode() : "",
                m.getQuantite(),
                m.getMotif(),
                m.getUtilisateur() != null
                        ? (m.getUtilisateur().getPrenom() + " " + m.getUtilisateur().getNom()).trim()
                        : "");
    }

    // --- Compteurs ---

    private Compteurs compteurs(Map<Long, Agregat> parProduit,
                                Map<Long, LocalDateTime> derniereSortie,
                                LocalDateTime depuis) {
        int ruptures = 0;
        int toutReserve = 0;
        int dormants = 0;
        for (Agregat a : parProduit.values()) {
            if (a.disponible().signum() <= 0) {
                ruptures++;
                if (a.quantite.signum() > 0) {
                    toutReserve++;
                }
            }
            if (a.quantite.signum() > 0 && estDormant(a.produitId, derniereSortie, depuis)) {
                dormants++;
            }
        }
        int references = (int) produitRepository.count();
        return new Compteurs(
                references,
                parProduit.size(),
                Math.max(references - parProduit.size(), 0),
                ruptures,
                toutReserve,
                dormants,
                (int) depotRepository.count());
    }

    // --- Outils ---

    private Map<Long, Agregat> agregerParProduit(List<Photo> photo) {
        Map<Long, Agregat> parProduit = new LinkedHashMap<>();
        for (Photo l : photo) {
            Agregat agregat = parProduit.computeIfAbsent(l.produitId(),
                    id -> new Agregat(id, l.reference(), l.designation()));
            agregat.quantite = agregat.quantite.add(valeur(l.quantite()));
            agregat.quantiteReservee = agregat.quantiteReservee.add(valeur(l.quantiteReservee()));
            agregat.valeur = agregat.valeur.add(montant(l.quantite(), l.prixUnitaireHT()));
        }
        return parProduit;
    }

    private Map<Long, LocalDateTime> derniereSortieParProduit() {
        Map<Long, LocalDateTime> resultat = new HashMap<>();
        for (Object[] ligne : mouvementStockRepository.derniereSortieParProduit()) {
            resultat.put((Long) ligne[0], (LocalDateTime) ligne[1]);
        }
        return resultat;
    }

    private BigDecimal disponible(Photo ligne) {
        return valeur(ligne.quantite()).subtract(valeur(ligne.quantiteReservee()));
    }

    private BigDecimal montant(BigDecimal quantite, BigDecimal prix) {
        return valeur(quantite).multiply(valeur(prix));
    }

    private BigDecimal valeur(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** Cumul par produit, tous depots confondus. */
    private static final class Agregat {
        private final Long produitId;
        private final String reference;
        private final String designation;
        private BigDecimal quantite = BigDecimal.ZERO;
        private BigDecimal quantiteReservee = BigDecimal.ZERO;
        private BigDecimal valeur = BigDecimal.ZERO;

        private Agregat(Long produitId, String reference, String designation) {
            this.produitId = produitId;
            this.reference = reference;
            this.designation = designation;
        }

        private BigDecimal disponible() {
            return quantite.subtract(quantiteReservee);
        }
    }
}
