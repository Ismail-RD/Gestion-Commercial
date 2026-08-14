package com.example.gestioncommerciale.specification;

import com.example.gestioncommerciale.dto.filter.FournisseurFilter;
import com.example.gestioncommerciale.entity.Fournisseur;
import com.example.gestioncommerciale.entity.FournisseurEntreprise;
import com.example.gestioncommerciale.entity.FournisseurParticulier;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

import static com.example.gestioncommerciale.specification.ClientSpecifications.like;

public final class FournisseurSpecifications {

    private FournisseurSpecifications() {
    }

    public static Specification<Fournisseur> avecFiltre(FournisseurFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Recherche libre, y compris sur les champs des sous-classes.
            if (f.recherche() != null && !f.recherche().isBlank()) {
                String motif = "%" + f.recherche().trim().toLowerCase() + "%";
                Root<FournisseurEntreprise> entreprise = cb.treat(root, FournisseurEntreprise.class);
                Root<FournisseurParticulier> particulier = cb.treat(root, FournisseurParticulier.class);
                predicates.add(cb.or(
                        like(cb, root.get("nom"), motif),
                        like(cb, root.get("email"), motif),
                        like(cb, root.get("adresse"), motif),
                        like(cb, entreprise.get("raisonSociale"), motif),
                        like(cb, entreprise.get("ice"), motif),
                        like(cb, entreprise.get("identifiantFiscal"), motif),
                        like(cb, particulier.get("prenom"), motif),
                        like(cb, particulier.get("cin"), motif)));
            }

            if (f.nom() != null && !f.nom().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("nom")),
                        "%" + f.nom().toLowerCase() + "%"));
            }
            if (f.email() != null && !f.email().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("email")),
                        "%" + f.email().toLowerCase() + "%"));
            }
            if (f.typeFournisseur() != null) {
                predicates.add(cb.equal(root.get("typeFournisseur"), f.typeFournisseur()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
