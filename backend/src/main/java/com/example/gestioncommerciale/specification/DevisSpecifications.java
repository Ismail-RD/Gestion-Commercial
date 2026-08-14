package com.example.gestioncommerciale.specification;

import com.example.gestioncommerciale.dto.filter.DevisFilter;
import com.example.gestioncommerciale.entity.Devis;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class DevisSpecifications {

    private DevisSpecifications() {
    }

    public static Specification<Devis> avecFiltre(DevisFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Recherche libre : le terme est confronte au numero, a la reference
            // et aux noms du client et du commercial.
            if (f.recherche() != null && !f.recherche().isBlank()) {
                String motif = "%" + f.recherche().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        ClientSpecifications.like(cb, root.get("numero"), motif),
                        ClientSpecifications.like(cb, root.get("reference"), motif),
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
            if (f.commercialId() != null) {
                predicates.add(cb.equal(root.get("commercial").get("id"), f.commercialId()));
            }
            // Bornes de dates incluses : dateMax couvre toute la journee.
            if (f.dateMin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dateCreation"),
                        f.dateMin().atStartOfDay()));
            }
            if (f.dateMax() != null) {
                predicates.add(cb.lessThan(root.get("dateCreation"),
                        f.dateMax().plusDays(1).atStartOfDay()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
