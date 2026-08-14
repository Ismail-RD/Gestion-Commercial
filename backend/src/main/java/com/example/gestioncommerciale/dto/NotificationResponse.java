package com.example.gestioncommerciale.dto;

import com.example.gestioncommerciale.entity.NiveauNotification;
import com.example.gestioncommerciale.entity.TypeDocument;
import com.example.gestioncommerciale.entity.TypeNotification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        TypeNotification type,
        NiveauNotification niveau,
        String titre,
        String message,
        /** Document a ouvrir au clic ; nuls quand la notification ne renvoie nulle part. */
        TypeDocument typeDocument,
        Long documentId,
        LocalDateTime dateCreation,
        LocalDateTime dateLecture
) {
}
