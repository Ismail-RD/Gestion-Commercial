package com.example.gestioncommerciale.service.impl;

import com.example.gestioncommerciale.dto.MarqueResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.ProduitFournisseurRequest;
import com.example.gestioncommerciale.dto.ProduitFournisseurResponse;
import com.example.gestioncommerciale.dto.ProduitRequest;
import com.example.gestioncommerciale.dto.ProduitResponse;
import com.example.gestioncommerciale.dto.filter.ProduitFilter;
import com.example.gestioncommerciale.entity.Categorie;
import com.example.gestioncommerciale.entity.Fournisseur;
import com.example.gestioncommerciale.entity.Marque;
import com.example.gestioncommerciale.entity.Produit;
import com.example.gestioncommerciale.entity.ProduitFournisseur;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.CategorieRepository;
import com.example.gestioncommerciale.repository.FournisseurRepository;
import com.example.gestioncommerciale.repository.MarqueRepository;
import com.example.gestioncommerciale.repository.ProduitRepository;
import com.example.gestioncommerciale.service.FileStorageService;
import com.example.gestioncommerciale.service.ProduitService;
import com.example.gestioncommerciale.specification.ProduitSpecifications;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class ProduitServiceImpl implements ProduitService {

    private final ProduitRepository produitRepository;
    private final CategorieRepository categorieRepository;
    private final MarqueRepository marqueRepository;
    private final FournisseurRepository fournisseurRepository;
    private final FileStorageService fileStorageService;

    public ProduitServiceImpl(ProduitRepository produitRepository,
                              CategorieRepository categorieRepository,
                              MarqueRepository marqueRepository,
                              FournisseurRepository fournisseurRepository,
                              FileStorageService fileStorageService) {
        this.produitRepository = produitRepository;
        this.categorieRepository = categorieRepository;
        this.marqueRepository = marqueRepository;
        this.fournisseurRepository = fournisseurRepository;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public ProduitResponse creer(ProduitRequest request) {
        if (produitRepository.existsByReference(request.reference())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La reference '" + request.reference() + "' existe deja");
        }
        Produit produit = new Produit();
        appliquer(produit, request);
        return toResponse(produitRepository.save(produit));
    }

    @Override
    public ProduitResponse modifier(Long id, ProduitRequest request) {
        Produit produit = getOrThrow(id);
        if (produitRepository.existsByReferenceAndIdNot(request.reference(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La reference '" + request.reference() + "' existe deja");
        }
        appliquer(produit, request);
        return toResponse(produitRepository.save(produit));
    }

    @Override
    @Transactional(readOnly = true)
    public ProduitResponse trouverParId(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProduitResponse> lister(ProduitFilter filtre, Pageable pageable) {
        return PageResponse.from(
                produitRepository.findAll(ProduitSpecifications.avecFiltre(filtre), pageable)
                        .map(this::toResponse));
    }

    @Override
    public void supprimer(Long id) {
        if (!produitRepository.existsById(id)) {
            throw new ResourceNotFoundException("Produit", id);
        }
        produitRepository.deleteById(id);
    }

    // --- Fiche technique ---

    @Override
    public ProduitResponse ajouterFicheTechnique(Long id, MultipartFile fichier) {
        Produit produit = getOrThrow(id);
        String ancien = produit.getFicheTechnique();
        String chemin = fileStorageService.stocker(fichier, "produit-" + id);
        produit.setFicheTechnique(chemin);
        produitRepository.save(produit);
        // Remplacement : on efface l'ancien fichier seulement une fois le nouveau en place.
        if (ancien != null && !ancien.equals(chemin)) {
            fileStorageService.supprimer(ancien);
        }
        return toResponse(produit);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource telechargerFicheTechnique(Long id) {
        Produit produit = getOrThrow(id);
        if (produit.getFicheTechnique() == null) {
            throw new ResourceNotFoundException("Fiche technique du produit", id);
        }
        return fileStorageService.charger(produit.getFicheTechnique());
    }

    @Override
    public ProduitResponse supprimerFicheTechnique(Long id) {
        Produit produit = getOrThrow(id);
        String chemin = produit.getFicheTechnique();
        if (chemin == null) {
            throw new ResourceNotFoundException("Fiche technique du produit", id);
        }
        produit.setFicheTechnique(null);
        produitRepository.save(produit);
        fileStorageService.supprimer(chemin);
        return toResponse(produit);
    }

    private Produit getOrThrow(Long id) {
        return produitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produit", id));
    }

    private void appliquer(Produit produit, ProduitRequest request) {
        produit.setReference(request.reference());
        produit.setDesignation(request.designation());
        produit.setDescription(request.description());
        produit.setPrixUnitaireHT(request.prixUnitaireHT());
        produit.setTauxTVA(request.tauxTVA());
        produit.setUniteMesure(request.uniteMesure());
        produit.setCategorie(resoudreCategorie(request.categorieId()));

        // Marques
        if (request.marqueIds() != null) {
            Set<Marque> marques = new HashSet<>(marqueRepository.findAllById(request.marqueIds()));
            produit.setMarques(marques);
        }

        // Fournisseurs : fusion avec l'existant. Vider puis recreer une liaison de
        // meme cle composite (produit, fournisseur) dans la meme transaction fait
        // coexister deux entites de meme id dans le contexte de persistance
        // -> DuplicateKeyException. On met donc a jour les liaisons conservees,
        // on retire les deselectionnees, et on ne cree que les vraies nouvelles.
        if (request.fournisseurs() != null) {
            Map<Long, ProduitFournisseurRequest> souhaites = new LinkedHashMap<>();
            for (ProduitFournisseurRequest lien : request.fournisseurs()) {
                souhaites.put(lien.fournisseurId(), lien);
            }
            produit.getFournisseurs().removeIf(pf -> {
                ProduitFournisseurRequest maj = souhaites.remove(pf.getFournisseur().getId());
                if (maj == null) {
                    return true; // deselectionne -> orphanRemoval le supprime
                }
                pf.setReferenceFournisseur(maj.referenceFournisseur());
                pf.setEstPrincipal(Boolean.TRUE.equals(maj.estPrincipal()));
                return false;
            });
            for (ProduitFournisseurRequest lien : souhaites.values()) {
                Fournisseur fournisseur = fournisseurRepository.findById(lien.fournisseurId())
                        .orElseThrow(() -> new ResourceNotFoundException("Fournisseur", lien.fournisseurId()));
                produit.ajouterFournisseur(
                        fournisseur,
                        lien.referenceFournisseur(),
                        Boolean.TRUE.equals(lien.estPrincipal()));
            }
        }
    }

    private Categorie resoudreCategorie(Long categorieId) {
        if (categorieId == null) {
            return null;
        }
        return categorieRepository.findById(categorieId)
                .orElseThrow(() -> new ResourceNotFoundException("Categorie", categorieId));
    }

    private MarqueResponse marqueToResponse(Marque m) {
        return new MarqueResponse(m.getId(), m.getNom(), m.getLogo(), m.getTelephone(),
                m.getEmail(), m.getAdresse(), m.getSiteWeb());
    }

    private ProduitFournisseurResponse lienToResponse(ProduitFournisseur pf) {
        Fournisseur f = pf.getFournisseur();
        return new ProduitFournisseurResponse(
                f.getId(),
                f.getNom(),
                f.getTypeFournisseur(),
                pf.getReferenceFournisseur(),
                pf.getEstPrincipal());
    }

    private ProduitResponse toResponse(Produit p) {
        Categorie c = p.getCategorie();
        List<MarqueResponse> marques = p.getMarques().stream().map(this::marqueToResponse).toList();
        List<ProduitFournisseurResponse> fournisseurs = p.getFournisseurs().stream().map(this::lienToResponse).toList();

        return new ProduitResponse(
                p.getId(),
                p.getReference(),
                p.getDesignation(),
                p.getDescription(),
                p.getPrixUnitaireHT(),
                p.getCoutRevientMoyen(),
                p.getTauxTVA(),
                p.getUniteMesure(),
                p.getFicheTechnique(),
                c != null ? c.getId() : null,
                c != null ? c.getNom() : null,
                marques,
                fournisseurs
        );
    }
}
