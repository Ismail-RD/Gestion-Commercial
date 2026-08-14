package com.example.gestioncommerciale.service.impl;

import com.example.gestioncommerciale.dto.CategorieRequest;
import com.example.gestioncommerciale.dto.CategorieResponse;
import com.example.gestioncommerciale.entity.Categorie;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.CategorieRepository;
import com.example.gestioncommerciale.service.CategorieService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CategorieServiceImpl implements CategorieService {

    private final CategorieRepository categorieRepository;

    public CategorieServiceImpl(CategorieRepository categorieRepository) {
        this.categorieRepository = categorieRepository;
    }

    @Override
    public CategorieResponse creer(CategorieRequest request) {
        Categorie categorie = Categorie.builder()
                .nom(request.nom())
                .description(request.description())
                .build();
        return toResponse(categorieRepository.save(categorie));
    }

    @Override
    public CategorieResponse modifier(Long id, CategorieRequest request) {
        Categorie categorie = getOrThrow(id);
        categorie.setNom(request.nom());
        categorie.setDescription(request.description());
        return toResponse(categorieRepository.save(categorie));
    }

    @Override
    @Transactional(readOnly = true)
    public CategorieResponse trouverParId(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategorieResponse> lister() {
        return categorieRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public void supprimer(Long id) {
        if (!categorieRepository.existsById(id)) {
            throw new ResourceNotFoundException("Categorie", id);
        }
        categorieRepository.deleteById(id);
    }

    private Categorie getOrThrow(Long id) {
        return categorieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categorie", id));
    }

    private CategorieResponse toResponse(Categorie c) {
        return new CategorieResponse(c.getId(), c.getNom(), c.getDescription());
    }
}
