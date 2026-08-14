package com.example.gestioncommerciale.specification;

import com.example.gestioncommerciale.dto.filter.FactureFilter;
import com.example.gestioncommerciale.entity.Facture;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class FactureSpecifications {

    private FactureSpecifications() {
    }

    public static Specification<Facture> avecFiltre(FactureFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Recherche libre : numero + nom du client.
            if (f.recherche() != null && !f.recherche().isBlank()) {
                String motif = "%" + f.recherche().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        ClientSpecifications.like(cb, root.get("numero"), motif),
                        ClientSpecifications.like(cb, root.get("client").get("nom"), motif)));
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
            // Bornes de dates incluses : dateMax couvre toute la journee.
            if (f.dateMin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dateFacture"),
                        f.dateMin().atStartOfDay()));
            }
            if (f.dateMax() != null) {
                predicates.add(cb.lessThan(root.get("dateFacture"),
                        f.dateMax().plusDays(1).atStartOfDay()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
