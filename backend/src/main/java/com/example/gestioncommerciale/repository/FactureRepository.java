package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.Facture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FactureRepository extends JpaRepository<Facture, Long>,
        JpaSpecificationExecutor<Facture> {

    /** Plus haut numero attribue pour ce prefixe, base de la numerotation. */
    @Query("select max(f.numero) from Facture f where f.numero like concat(:prefix, '%')")
    String dernierNumero(@Param("prefix") String prefix);

    boolean existsByClientId(Long clientId);

    /** Une commande ne se facture qu'une fois : sert a refuser le doublon. */
    Optional<Facture> findFirstByCommandeId(Long commandeId);

    /**
     * Factures dont l'echeance est passee sans que le statut l'ait enregistre.
     * Le retard est la seule evolution qu'aucune action utilisateur ne
     * declenche : il faut aller la chercher.
     */
    @Query("select f from Facture f where f.dateEcheance < :date "
            + "and f.montantTTC > f.montantPaye "
            + "and f.statut in (com.example.gestioncommerciale.entity.StatutFacture.EMISE, "
            + "com.example.gestioncommerciale.entity.StatutFacture.PARTIELLEMENT_PAYEE)")
    List<Facture> echuesNonMarquees(@Param("date") LocalDate date);

    // --- Tableau de bord ---

    /** Chiffre facture sur une periode, annulations exclues. */
    @Query("select coalesce(sum(f.montantTTC), 0) from Facture f "
            + "where f.dateFacture >= :depuis "
            + "and f.statut <> com.example.gestioncommerciale.entity.StatutFacture.ANNULEE "
            + "and (:commercialId is null or f.client.commercial.id = :commercialId)")
    BigDecimal montantFactureDepuis(@Param("depuis") LocalDateTime depuis,
                                    @Param("commercialId") Long commercialId);

    /** Encours : ce que les clients doivent encore, toutes factures non soldees. */
    @Query("select coalesce(sum(f.montantTTC - f.montantPaye), 0) from Facture f "
            + "where f.statut not in (com.example.gestioncommerciale.entity.StatutFacture.PAYEE, "
            + "com.example.gestioncommerciale.entity.StatutFacture.ANNULEE) "
            + "and (:commercialId is null or f.client.commercial.id = :commercialId)")
    BigDecimal encours(@Param("commercialId") Long commercialId);

    /** Factures dont l'echeance est passee, les plus anciennes d'abord. */
    @Query("select f from Facture f "
            + "where f.statut = com.example.gestioncommerciale.entity.StatutFacture.EN_RETARD "
            + "and (:commercialId is null or f.client.commercial.id = :commercialId) "
            + "order by f.dateEcheance asc")
    List<Facture> enRetard(@Param("commercialId") Long commercialId);

    /**
     * Emises mais jamais transmises au client : elles ne seront pas payees.
     *
     * <p>Les factures deja soldees en sont exclues : reglees sans email, elles
     * ont ete remises autrement, et les faire figurer dans une file de travail
     * demanderait une action qui n'a plus lieu d'etre.
     */
    @Query("select f from Facture f where f.dateEnvoiEmail is null "
            + "and f.statut not in (com.example.gestioncommerciale.entity.StatutFacture.PAYEE, "
            + "com.example.gestioncommerciale.entity.StatutFacture.ANNULEE) "
            + "order by f.dateFacture asc")
    List<Facture> jamaisEnvoyees();

    /**
     * Dates et montants factures depuis une date, pour construire une serie
     * mensuelle. Le regroupement se fait en memoire : une annee de factures tient
     * dans une poignee de lignes, et l'extraction du mois en JPQL varie d'une
     * base a l'autre.
     */
    @Query("select f.dateFacture, f.montantTTC from Facture f where f.dateFacture >= :depuis "
            + "and f.statut <> com.example.gestioncommerciale.entity.StatutFacture.ANNULEE "
            + "and (:commercialId is null or f.client.commercial.id = :commercialId)")
    List<Object[]> montantsDepuis(@Param("depuis") LocalDateTime depuis,
                                  @Param("commercialId") Long commercialId);

    /** Chiffre facture par client, le plus gros d'abord. */
    @Query("select f.client.nom, sum(f.montantTTC) from Facture f "
            + "where f.statut <> com.example.gestioncommerciale.entity.StatutFacture.ANNULEE "
            + "and (:commercialId is null or f.client.commercial.id = :commercialId) "
            + "group by f.client.id, f.client.nom order by sum(f.montantTTC) desc")
    List<Object[]> chiffreParClient(@Param("commercialId") Long commercialId);

    /** Chiffre facture par commercial titulaire du client. */
    @Query("select f.client.commercial.id, sum(f.montantTTC) from Facture f "
            + "where f.statut <> com.example.gestioncommerciale.entity.StatutFacture.ANNULEE "
            + "and f.client.commercial is not null "
            + "group by f.client.commercial.id")
    List<Object[]> chiffreParCommercial();

    /** Echeance et reste du de chaque facture non soldee : base de la balance agee. */
    @Query("select f.dateEcheance, f.montantTTC - f.montantPaye from Facture f "
            + "where f.statut not in (com.example.gestioncommerciale.entity.StatutFacture.PAYEE, "
            + "com.example.gestioncommerciale.entity.StatutFacture.ANNULEE)")
    List<Object[]> restesDusParEcheance();

    /**
     * Encours du client = somme des restes a payer (montantTTC - montantPaye)
     * de ses factures non soldees. Sert au controle du plafond de credit.
     */
    @Query("select coalesce(sum(f.montantTTC - f.montantPaye), 0) from Facture f "
            + "where f.client.id = :clientId and f.statut <> "
            + "com.example.gestioncommerciale.entity.StatutFacture.PAYEE")
    BigDecimal encoursClient(@Param("clientId") Long clientId);
}
