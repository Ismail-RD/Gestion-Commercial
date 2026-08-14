package com.example.gestioncommerciale.specification;

import com.example.gestioncommerciale.dto.filter.StockFilter;
import com.example.gestioncommerciale.entity.StockProduit;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class StockProduitSpecifications {

    private StockProduitSpecifications() {
    }

    public static Specification<StockProduit> avecFiltre(StockFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Recherche libre : produit (reference/designation) ou code de depot.
            if (f.recherche() != null && !f.recherche().isBlank()) {
                String motif = "%" + f.recherche().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        ClientSpecifications.like(cb, root.get("produit").get("reference"), motif),
                        ClientSpecifications.like(cb, root.get("produit").get("designation"), motif),
                        ClientSpecifications.like(cb, root.get("depot").get("code"), motif)));
            }

            if (f.produitId() != null) {
                predicates.add(cb.equal(root.get("produit").get("id"), f.produitId()));
            }
            if (f.depotCode() != null && !f.depotCode().isBlank()) {
                predicates.add(cb.equal(root.get("depot").get("code"), f.depotCode()));
            }
            // Un niveau de stock a 0 signifie "plus en stock" : on l'ecarte sauf
            // demande explicite.
            if (!Boolean.TRUE.equals(f.inclureVides())) {
                predicates.add(cb.greaterThan(root.get("quantite"), 0));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
