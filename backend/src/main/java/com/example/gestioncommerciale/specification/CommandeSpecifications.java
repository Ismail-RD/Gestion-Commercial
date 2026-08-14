package com.example.gestioncommerciale.specification;

import com.example.gestioncommerciale.dto.filter.CommandeFilter;
import com.example.gestioncommerciale.entity.Commande;
import com.example.gestioncommerciale.entity.Facture;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class CommandeSpecifications {

    private CommandeSpecifications() {
    }

    public static Specification<Commande> avecFiltre(CommandeFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Recherche libre : numero + noms du client et du commercial.
            if (f.recherche() != null && !f.recherche().isBlank()) {
                String motif = "%" + f.recherche().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        ClientSpecifications.like(cb, root.get("numero"), motif),
                        ClientSpecifications.like(cb, root.get("client").get("nom"), motif),
                        ClientSpecifications.like(cb, root.get("commercial").get("nom"), motif),
                        ClientSpecifications.like(cb, root.get("commercial").get("prenom"), motif)));
            }

            if (f.numero() != null && !f.numero().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("numero")),
                        "%" + f.numero().toLowerCase() + "%"));
            }
            if (f.statut() != null) {
                predicates.add(cb.equal(root.get("statut"), f.statut()));
            }
            if (f.clientId() != null) {
                predicates.add(cb.equal(root.get("client").get("id"), f.clientId()));
            }
            if (f.devisId() != null) {
                predicates.add(cb.equal(root.get("devis").get("id"), f.devisId()));
            }
            // Alimente la liste des commandes facturables : une commande deja
            // facturee n'a plus a y figurer.
            if (Boolean.TRUE.equals(f.nonFacturee())) {
                Subquery<Long> dejaFacturee = query.subquery(Long.class);
                Root<Facture> facture = dejaFacturee.from(Facture.class);
                dejaFacturee.select(facture.get("id"))
                        .where(cb.equal(facture.get("commande"), root));
                predicates.add(cb.not(cb.exists(dejaFacturee)));
            }
            // Bornes de dates incluses : dateMax couvre toute la journee.
            if (f.dateMin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dateCommande"),
                        f.dateMin().atStartOfDay()));
            }
            if (f.dateMax() != null) {
                predicates.add(cb.lessThan(root.get("dateCommande"),
                        f.dateMax().plusDays(1).atStartOfDay()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
