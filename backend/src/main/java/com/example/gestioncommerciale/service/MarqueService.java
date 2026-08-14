package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.MarqueRequest;
import com.example.gestioncommerciale.dto.MarqueResponse;
import com.example.gestioncommerciale.dto.PageResponse;

import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MarqueService {

    MarqueResponse creer(MarqueRequest request);

    MarqueResponse modifier(Long id, MarqueRequest request);

    MarqueResponse trouverParId(Long id);

    PageResponse<MarqueResponse> lister(String nom, Pageable pageable);

    List<MarqueResponse> listerToutes();

    void supprimer(Long id);
}
