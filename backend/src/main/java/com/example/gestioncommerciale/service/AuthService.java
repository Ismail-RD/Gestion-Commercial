package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.AuthResponse;
import com.example.gestioncommerciale.dto.LoginRequest;
import com.example.gestioncommerciale.dto.RegisterRequest;
import com.example.gestioncommerciale.entity.Utilisateur;
import com.example.gestioncommerciale.repository.UtilisateurRepository;
import com.example.gestioncommerciale.security.JwtService;
import com.example.gestioncommerciale.security.UtilisateurDetails;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UtilisateurRepository utilisateurRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponse register(RegisterRequest request) {
        if (utilisateurRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet email est deja utilise");
        }

        Utilisateur utilisateur = Utilisateur.builder()
                .nom(request.nom())
                .prenom(request.prenom())
                .email(request.email())
                .motDePasse(passwordEncoder.encode(request.motDePasse()))
                .role(request.role())
                .actif(true)
                .build();

        Utilisateur sauvegarde = utilisateurRepository.save(utilisateur);
        String token = jwtService.genererToken(new UtilisateurDetails(sauvegarde));
        return toResponse(token, sauvegarde);
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.motDePasse()));

        UtilisateurDetails details = (UtilisateurDetails) authentication.getPrincipal();
        String token = jwtService.genererToken(details);
        return toResponse(token, details.getUtilisateur());
    }

    private AuthResponse toResponse(String token, Utilisateur u) {
        return new AuthResponse(token, u.getId(), u.getNom(), u.getPrenom(), u.getEmail(), u.getRole());
    }
}
