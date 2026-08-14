package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.FournisseurRequest;
import com.example.gestioncommerciale.dto.FournisseurResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.filter.FournisseurFilter;
import org.springframework.data.domain.Pageable;

public interface FournisseurService {

    FournisseurResponse creer(FournisseurRequest request);

    FournisseurResponse modifier(Long id, FournisseurRequest request);

    FournisseurResponse trouverParId(Long id);

    PageResponse<FournisseurResponse> lister(FournisseurFilter filtre, Pageable pageable);

    void supprimer(Long id);
}
