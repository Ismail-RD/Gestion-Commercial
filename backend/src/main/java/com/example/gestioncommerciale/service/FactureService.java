package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.FactureModificationRequest;
import com.example.gestioncommerciale.dto.FactureRequest;
import com.example.gestioncommerciale.dto.FactureResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.filter.FactureFilter;
import org.springframework.data.domain.Pageable;

public interface FactureService {

    FactureResponse creerDepuisCommande(FactureRequest request);

    /** Met a jour l'echeance d'une facture (seul champ renegociable). */
    FactureResponse modifier(Long id, FactureModificationRequest request);

    FactureResponse trouverParId(Long id);

    PageResponse<FactureResponse> lister(FactureFilter filtre, Pageable pageable);

    void supprimer(Long id);
}
