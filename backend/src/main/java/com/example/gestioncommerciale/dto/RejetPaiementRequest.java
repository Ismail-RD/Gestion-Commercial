package com.example.gestioncommerciale.dto;

import jakarta.validation.constraints.NotBlank;

/** Un rejet sans motif ne se traite pas : il faut savoir pourquoi rappeler le client. */
public record RejetPaiementRequest(
        @NotBlank(message = "Le motif du rejet est obligatoire") String motif
) {
}
