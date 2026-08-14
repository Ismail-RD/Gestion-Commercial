package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.CommandeFournisseur;
import com.example.gestioncommerciale.entity.StatutCommandeFournisseur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommandeFournisseurRepository extends JpaRepository<CommandeFournisseur, Long>,
        JpaSpecificationExecutor<CommandeFournisseur> {

    /** Plus haut numero attribue pour ce prefixe, base de la numerotation. */
    @Query("select max(c.numero) from CommandeFournisseur c "
            + "where c.numero like concat(:prefix, '%')")
    String dernierNumero(@Param("prefix") String prefix);

    /** Un fournisseur engage dans une commande ne peut pas etre efface. */
    boolean existsByFournisseurId(Long fournisseurId);

    /** Le depot de reception d'une commande en cours doit rester en place. */
    boolean existsByDepotReceptionId(Long depotId);

    @Query("select c from CommandeFournisseur c where c.statut in :statuts "
            + "order by c.dateArriveePrevue asc nulls last, c.dateCreation asc")
    List<CommandeFournisseur> parStatuts(
            @Param("statuts") java.util.Collection<StatutCommandeFournisseur> statuts);

    /**
     * Commandes dont la date d'arrivee annoncee est passee sans que la
     * marchandise soit entree. C'est le signal qui n'arrive jamais tout seul :
     * le fournisseur ne previent pas qu'il est en retard.
     */
    @Query("select c from CommandeFournisseur c where c.dateArriveePrevue < :date "
            + "and c.statut not in ("
            + "com.example.gestioncommerciale.entity.StatutCommandeFournisseur.RECEPTIONNEE, "
            + "com.example.gestioncommerciale.entity.StatutCommandeFournisseur.ANNULEE, "
            + "com.example.gestioncommerciale.entity.StatutCommandeFournisseur.BROUILLON) "
            + "order by c.dateArriveePrevue asc")
    List<CommandeFournisseur> enRetardArrivee(@Param("date") java.time.LocalDate date);

    /** Montant engage aupres des fournisseurs sur les dossiers non receptionnes. */
    @Query("select coalesce(sum(c.montantDevise * c.tauxChange), 0) from CommandeFournisseur c "
            + "where c.statut in :statuts")
    java.math.BigDecimal montantEngage(
            @Param("statuts") java.util.Collection<StatutCommandeFournisseur> statuts);
}
