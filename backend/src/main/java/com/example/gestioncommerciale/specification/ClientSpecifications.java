package com.example.gestioncommerciale.specification;

import com.example.gestioncommerciale.dto.filter.ClientFilter;
import com.example.gestioncommerciale.entity.Client;
import com.example.gestioncommerciale.entity.ClientEntreprise;
import com.example.gestioncommerciale.entity.ClientParticulier;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ClientSpecifications {

    private ClientSpecifications() {
    }

    public static Specification<Client> avecFiltre(ClientFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Recherche libre : un seul terme confronte a tous les champs
            // identifiants du client, y compris ceux portes par les sous-classes
            // (raison sociale / ICE / identifiant fiscal pour une entreprise,
            // prenom / CIN pour un particulier).
            if (f.recherche() != null && !f.recherche().isBlank()) {
                String motif = "%" + f.recherche().trim().toLowerCase() + "%";
                Root<ClientEntreprise> entreprise = cb.treat(root, ClientEntreprise.class);
                Root<ClientParticulier> particulier = cb.treat(root, ClientParticulier.class);
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
            if (f.typeClient() != null) {
                predicates.add(cb.equal(root.get("typeClient"), f.typeClient()));
            }
            if (f.commercialId() != null) {
                predicates.add(cb.equal(root.get("commercial").get("id"), f.commercialId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * LIKE insensible a la casse, tolerant au NULL : un champ absent (ou porte
     * par l'autre sous-classe) ne doit pas casser le OU logique.
     */
    static Predicate like(jakarta.persistence.criteria.CriteriaBuilder cb,
                          jakarta.persistence.criteria.Expression<String> champ,
                          String motif) {
        return cb.like(cb.lower(cb.coalesce(champ, "")), motif);
    }
}
