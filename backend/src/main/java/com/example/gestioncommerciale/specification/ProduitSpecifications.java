package com.example.gestioncommerciale.specification;

import com.example.gestioncommerciale.dto.filter.ProduitFilter;
import com.example.gestioncommerciale.entity.Produit;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ProduitSpecifications {

    private ProduitSpecifications() {
    }

    public static Specification<Produit> avecFiltre(ProduitFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Recherche libre : reference, designation, description et nom de
            // categorie (jointure gauche : un produit sans categorie reste visible).
            if (f.recherche() != null && !f.recherche().isBlank()) {
                String motif = "%" + f.recherche().trim().toLowerCase() + "%";
                var categorie = root.join("categorie", jakarta.persistence.criteria.JoinType.LEFT);
                predicates.add(cb.or(
                        ClientSpecifications.like(cb, root.get("reference"), motif),
                        ClientSpecifications.like(cb, root.get("designation"), motif),
                        ClientSpecifications.like(cb, root.get("description"), motif),
                        ClientSpecifications.like(cb, categorie.get("nom"), motif)));
            }
            if (f.reference() != null && !f.reference().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("reference")),
                        "%" + f.reference().toLowerCase() + "%"));
            }
            if (f.designation() != null && !f.designation().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("designation")),
                        "%" + f.designation().toLowerCase() + "%"));
            }
            if (f.categorieId() != null) {
                predicates.add(cb.equal(root.get("categorie").get("id"), f.categorieId()));
            }
            if (f.prixMin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("prixUnitaireHT"), f.prixMin()));
            }
            if (f.prixMax() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("prixUnitaireHT"), f.prixMax()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
