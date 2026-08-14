package com.example.gestioncommerciale.service.impl;

import com.example.gestioncommerciale.dto.FournisseurRequest;
import com.example.gestioncommerciale.dto.FournisseurResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.RibDto;
import com.example.gestioncommerciale.dto.filter.FournisseurFilter;
import com.example.gestioncommerciale.entity.Fournisseur;
import com.example.gestioncommerciale.entity.FournisseurEntreprise;
import com.example.gestioncommerciale.entity.FournisseurParticulier;
import com.example.gestioncommerciale.entity.Rib;
import com.example.gestioncommerciale.entity.TypeFournisseur;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.CommandeFournisseurRepository;
import com.example.gestioncommerciale.repository.FournisseurRepository;
import com.example.gestioncommerciale.service.FournisseurService;
import com.example.gestioncommerciale.specification.FournisseurSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class FournisseurServiceImpl implements FournisseurService {

    private final FournisseurRepository fournisseurRepository;
    private final CommandeFournisseurRepository commandeFournisseurRepository;

    public FournisseurServiceImpl(FournisseurRepository fournisseurRepository,
                                  CommandeFournisseurRepository commandeFournisseurRepository) {
        this.fournisseurRepository = fournisseurRepository;
        this.commandeFournisseurRepository = commandeFournisseurRepository;
    }

    @Override
    public FournisseurResponse creer(FournisseurRequest request) {
        if (request.email() != null && fournisseurRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un fournisseur avec l'email '" + request.email() + "' existe deja");
        }
        Fournisseur fournisseur = creerEntite(request);
        appliquer(fournisseur, request);
        return toResponse(fournisseurRepository.save(fournisseur));
    }

    @Override
    public FournisseurResponse modifier(Long id, FournisseurRequest request) {
        Fournisseur fournisseur = getOrThrow(id);
        if (request.email() != null && fournisseurRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un fournisseur avec l'email '" + request.email() + "' existe deja");
        }
        appliquer(fournisseur, request);
        return toResponse(fournisseurRepository.save(fournisseur));
    }

    @Override
    @Transactional(readOnly = true)
    public FournisseurResponse trouverParId(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FournisseurResponse> lister(FournisseurFilter filtre, Pageable pageable) {
        return PageResponse.from(
                fournisseurRepository.findAll(FournisseurSpecifications.avecFiltre(filtre), pageable)
                        .map(this::toResponse));
    }

    @Override
    public void supprimer(Long id) {
        Fournisseur fournisseur = getOrThrow(id);
        // Un fournisseur chez qui on a deja commande figure sur des engagements :
        // l'effacer rendrait ces commandes orphelines.
        if (commandeFournisseurRepository.existsByFournisseurId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Des commandes ont ete passees a ce fournisseur : elles gardent sa trace");
        }
        fournisseurRepository.delete(fournisseur);
    }

    private Fournisseur getOrThrow(Long id) {
        return fournisseurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fournisseur", id));
    }

    private Fournisseur creerEntite(FournisseurRequest request) {
        if (request.typeFournisseur() == TypeFournisseur.ENTREPRISE) {
            return FournisseurEntreprise.builder().build();
        }
        return FournisseurParticulier.builder().build();
    }

    private void appliquer(Fournisseur fournisseur, FournisseurRequest request) {
        fournisseur.setNom(request.nom());
        fournisseur.setEmail(request.email());
        fournisseur.setTelephones(nettoyerTelephones(request.telephones()));
        fournisseur.setRibs(versRibs(request.ribs()));
        fournisseur.setAdresse(request.adresse());

        if (fournisseur instanceof FournisseurEntreprise entreprise) {
            entreprise.setRaisonSociale(request.raisonSociale());
            // Un champ laisse vide dans le formulaire arrive en "" : l'absence
            // d'ICE / d'identifiant fiscal doit s'exprimer par NULL (les
            // contraintes base refusent "").
            entreprise.setIce(videEnNull(request.ice()));
            entreprise.setIdentifiantFiscal(videEnNull(request.identifiantFiscal()));
        } else if (fournisseur instanceof FournisseurParticulier particulier) {
            particulier.setPrenom(request.prenom());
            particulier.setCin(videEnNull(request.cin()));
        }
    }

    /** Une chaine vide venant d'un formulaire signifie "non renseigne" -> NULL. */
    private String videEnNull(String valeur) {
        return (valeur == null || valeur.isBlank()) ? null : valeur.trim();
    }

    /** Nettoie une liste de telephones : retire les valeurs vides. */
    private List<String> nettoyerTelephones(List<String> telephones) {
        if (telephones == null) {
            return new ArrayList<>();
        }
        return telephones.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Rib> versRibs(List<RibDto> ribs) {
        if (ribs == null) {
            return new ArrayList<>();
        }
        return ribs.stream()
                .filter(r -> r != null && r.rib() != null && !r.rib().isBlank())
                .map(r -> new Rib(r.rib().trim(), videEnNull(r.banque())))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<RibDto> versRibDtos(List<Rib> ribs) {
        return ribs.stream()
                .map(r -> new RibDto(r.getRib(), r.getBanque()))
                .toList();
    }

    private FournisseurResponse toResponse(Fournisseur f) {
        String raisonSociale = null, ice = null, identifiantFiscal = null, prenom = null, cin = null;

        if (f instanceof FournisseurEntreprise e) {
            raisonSociale = e.getRaisonSociale();
            ice = e.getIce();
            identifiantFiscal = e.getIdentifiantFiscal();
        } else if (f instanceof FournisseurParticulier p) {
            prenom = p.getPrenom();
            cin = p.getCin();
        }

        return new FournisseurResponse(
                f.getId(),
                f.getNom(),
                f.getEmail(),
                f.getTelephones(),
                versRibDtos(f.getRibs()),
                f.getAdresse(),
                f.getTypeFournisseur(),
                f.getDateCreation(),
                raisonSociale,
                ice,
                identifiantFiscal,
                prenom,
                cin
        );
    }
}
