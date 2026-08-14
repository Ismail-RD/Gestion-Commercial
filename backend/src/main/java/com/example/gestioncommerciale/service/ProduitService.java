package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.ProduitRequest;
import com.example.gestioncommerciale.dto.ProduitResponse;
import com.example.gestioncommerciale.dto.filter.ProduitFilter;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ProduitService {

    ProduitResponse creer(ProduitRequest request);

    ProduitResponse modifier(Long id, ProduitRequest request);

    ProduitResponse trouverParId(Long id);

    PageResponse<ProduitResponse> lister(ProduitFilter filtre, Pageable pageable);

    void supprimer(Long id);

    // --- Fiche technique ---

    /** Enregistre (ou remplace) la fiche technique du produit. */
    ProduitResponse ajouterFicheTechnique(Long id, MultipartFile fichier);

    /** Charge la fiche technique pour telechargement (404 si absente). */
    Resource telechargerFicheTechnique(Long id);

    /** Supprime la fiche technique du produit. */
    ProduitResponse supprimerFicheTechnique(Long id);
}
