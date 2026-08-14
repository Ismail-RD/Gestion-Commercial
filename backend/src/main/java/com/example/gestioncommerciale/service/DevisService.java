package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.DevisRequest;
import com.example.gestioncommerciale.dto.DevisResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.ReponseClientRequest;
import com.example.gestioncommerciale.dto.filter.DevisFilter;
import org.springframework.data.domain.Pageable;

public interface DevisService {

    DevisResponse creer(DevisRequest request);

    DevisResponse modifier(Long id, DevisRequest request);

    DevisResponse trouverParId(Long id);

    PageResponse<DevisResponse> lister(DevisFilter filtre, Pageable pageable);

    void supprimer(Long id);

    // --- Workflow ---

    DevisResponse envoyer(Long id);

    DevisResponse accepter(Long id, ReponseClientRequest reponse);

    DevisResponse refuser(Long id, ReponseClientRequest reponse);

    /** Valide un devis en attente (remise > seuil) : passe a ENVOYE. Reservee a l'admin. */
    DevisResponse validerRemise(Long id);

    /** Refuse la remise d'un devis en attente : retour a BROUILLON. Reservee a l'admin. */
    DevisResponse refuserRemise(Long id);
}
