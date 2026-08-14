package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface PaiementRepository extends JpaRepository<Paiement, Long> {

    List<Paiement> findByFactureId(Long factureId);

    /** Un paiement rattache, meme rejete, empeche d effacer la facture. */
    long countByFactureId(Long factureId);

    /** Total encaisse sur une facture : la seule base du montant paye. */
    @Query("select coalesce(sum(p.montant), 0) from Paiement p where p.facture.id = :factureId "
            + "and p.statut = com.example.gestioncommerciale.entity.StatutPaiement.ENCAISSE")
    BigDecimal totalEncaisse(@Param("factureId") Long factureId);

    /** Encaisse plus effets en attente : ce qui est deja promis sur la facture. */
    @Query("select coalesce(sum(p.montant), 0) from Paiement p where p.facture.id = :factureId "
            + "and p.statut <> com.example.gestioncommerciale.entity.StatutPaiement.REJETE")
    BigDecimal totalEngage(@Param("factureId") Long factureId);

    /** Portefeuille d effets : les plus proches de l echeance d abord. */
    @Query("select p from Paiement p where p.statut in ("
            + "com.example.gestioncommerciale.entity.StatutPaiement.RECU, "
            + "com.example.gestioncommerciale.entity.StatutPaiement.DEPOSE) "
            + "order by p.dateEcheance asc nulls last, p.dateReception asc")
    List<Paiement> effetsEnAttente();

    /** Ce qui est reellement rentre en caisse sur la periode. */
    @Query("select coalesce(sum(p.montant), 0) from Paiement p where p.datePaiement >= :depuis "
            + "and p.statut = com.example.gestioncommerciale.entity.StatutPaiement.ENCAISSE")
    BigDecimal encaisseDepuis(@Param("depuis") LocalDateTime depuis);
}
