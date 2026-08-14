package com.example.gestioncommerciale.service.impl;

import com.example.gestioncommerciale.dto.MarqueRequest;
import com.example.gestioncommerciale.dto.MarqueResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.entity.Marque;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.MarqueRepository;
import com.example.gestioncommerciale.service.MarqueService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class MarqueServiceImpl implements MarqueService {

    private final MarqueRepository marqueRepository;

    public MarqueServiceImpl(MarqueRepository marqueRepository) {
        this.marqueRepository = marqueRepository;
    }

    @Override
    public MarqueResponse creer(MarqueRequest request) {
        if (marqueRepository.existsByNom(request.nom())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La marque '" + request.nom() + "' existe deja");
        }
        Marque marque = new Marque();
        appliquer(marque, request);
        return toResponse(marqueRepository.save(marque));
    }

    @Override
    public MarqueResponse modifier(Long id, MarqueRequest request) {
        Marque marque = getOrThrow(id);
        if (marqueRepository.existsByNomAndIdNot(request.nom(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La marque '" + request.nom() + "' existe deja");
        }
        appliquer(marque, request);
        return toResponse(marqueRepository.save(marque));
    }

    @Override
    @Transactional(readOnly = true)
    public MarqueResponse trouverParId(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MarqueResponse> lister(String nom, Pageable pageable) {
        Page<Marque> page;
        if (nom != null && !nom.isBlank()) {
            Specification<Marque> spec = (root, query, cb) ->
                    cb.like(cb.lower(root.get("nom")), "%" + nom.toLowerCase() + "%");
            page = marqueRepository.findAll(spec, pageable);
        } else {
            page = marqueRepository.findAll(pageable);
        }
        return PageResponse.from(page.map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarqueResponse> listerToutes() {
        return marqueRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public void supprimer(Long id) {
        Marque marque = getOrThrow(id);
        marqueRepository.delete(marque);
    }

    private Marque getOrThrow(Long id) {
        return marqueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marque", id));
    }

    private void appliquer(Marque marque, MarqueRequest request) {
        marque.setNom(request.nom());
        marque.setLogo(request.logo());
        marque.setTelephone(request.telephone());
        marque.setEmail(request.email());
        marque.setAdresse(request.adresse());
        marque.setSiteWeb(request.siteWeb());
    }

    private MarqueResponse toResponse(Marque m) {
        return new MarqueResponse(
                m.getId(),
                m.getNom(),
                m.getLogo(),
                m.getTelephone(),
                m.getEmail(),
                m.getAdresse(),
                m.getSiteWeb()
        );
    }
}
