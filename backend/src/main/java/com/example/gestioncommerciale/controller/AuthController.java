package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.AuthResponse;
import com.example.gestioncommerciale.dto.LoginRequest;
import com.example.gestioncommerciale.dto.RegisterRequest;
import com.example.gestioncommerciale.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Creation d'un compte. Reservee a l'admin : la requete porte le role du
     * nouvel utilisateur, un acces libre permettrait a n'importe qui de se
     * fabriquer un compte ADMIN.
     */
    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
