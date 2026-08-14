package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.PouvoirRoleRequest;
import com.example.gestioncommerciale.dto.PouvoirRoleResponse;
import com.example.gestioncommerciale.entity.PouvoirRole;
import com.example.gestioncommerciale.entity.Role;
import com.example.gestioncommerciale.repository.PouvoirRoleRepository;
import com.example.gestioncommerciale.security.Autorisations;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Parametres de l'application, regles par l'administrateur.
 *
 * <p>Les pouvoirs sont en lecture pour tous ceux qui redigent des documents :
 * chacun a besoin de savoir jusqu'ou il engage l'entreprise sans demander l'aval
 * de sa hierarchie.
 */
@RestController
@RequestMapping("/api/parametres")
public class ParametreController {

    /** Roles dotes de pouvoirs bornes. L'ADMIN n'en a pas : rien ne le depasse. */
    private static final List<Role> ROLES_BORNES =
            List.of(Role.RESPONSABLE_COMMERCIAL, Role.COMMERCIAL);

    private final PouvoirRoleRepository pouvoirRepository;

    public ParametreController(PouvoirRoleRepository pouvoirRepository) {
        this.pouvoirRepository = pouvoirRepository;
    }

    @GetMapping("/pouvoirs")
    @PreAuthorize(Autorisations.LIRE_COMMERCIAL)
    public List<PouvoirRoleResponse> lister() {
        return pouvoirRepository.findAll(Sort.by("role")).stream()
                .map(this::toResponse)
                .toList();
    }

    @PutMapping("/pouvoirs/{role}")
    @PreAuthorize(Autorisations.ADMINISTRER)
    public PouvoirRoleResponse modifier(@PathVariable Role role,
                                        @Valid @RequestBody PouvoirRoleRequest request) {
        if (!ROLES_BORNES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le role " + role + " n'a pas de pouvoirs bornes");
        }
        // Le commercial n'attribue pas de credit : lui poser un plafond n'aurait
        // aucun effet et laisserait croire le contraire.
        if (role == Role.COMMERCIAL && request.plafondCreditMax() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le commercial ne fixe pas les plafonds de credit de ses clients");
        }
        PouvoirRole pouvoir = pouvoirRepository.findById(role)
                .orElseGet(() -> PouvoirRole.builder().role(role).build());
        pouvoir.setSeuilRemisePct(request.seuilRemisePct());
        pouvoir.setPlafondCreditMax(request.plafondCreditMax());
        return toResponse(pouvoirRepository.save(pouvoir));
    }

    private PouvoirRoleResponse toResponse(PouvoirRole p) {
        return new PouvoirRoleResponse(p.getRole(), p.getSeuilRemisePct(), p.getPlafondCreditMax());
    }
}
