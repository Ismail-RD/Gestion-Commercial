package com.example.gestioncommerciale.specification;

import com.example.gestioncommerciale.dto.filter.MouvementFilter;
import com.example.gestioncommerciale.entity.MouvementStock;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class MouvementStockSpecifications {

    private MouvementStockSpecifications() {
    }

    public static Specification<MouvementStock> avecFiltre(MouvementFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Recherche libre : produit (reference/designation), code de depot, motif.
            if (f.recherche() != null && !f.recherche().isBlank()) {
                String motif = "%" + f.recherche().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        ClientSpecifications.like(cb, root.get("produit").get("reference"), motif),
                        ClientSpecifications.like(cb, root.get("produit").get("designation"), motif),
                        ClientSpecifications.like(cb, root.get("depot").get("code"), motif),
                        ClientSpecifications.like(cb, root.get("motif"), motif)));
            }

            if (f.produitId() != null) {
                predicates.add(cb.equal(root.get("produit").get("id"), f.produitId()));
            }
            if (f.depotCode() != null && !f.depotCode().isBlank()) {
                predicates.add(cb.equal(root.get("depot").get("code"), f.depotCode()));
            }
            if (f.type() != null) {
                predicates.add(cb.equal(root.get("type"), f.type()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
