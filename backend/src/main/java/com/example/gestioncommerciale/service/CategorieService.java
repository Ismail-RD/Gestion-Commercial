package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.CategorieRequest;
import com.example.gestioncommerciale.dto.CategorieResponse;

import java.util.List;

public interface CategorieService {

    CategorieResponse creer(CategorieRequest request);

    CategorieResponse modifier(Long id, CategorieRequest request);

    CategorieResponse trouverParId(Long id);

    List<CategorieResponse> lister();

    void supprimer(Long id);
}
