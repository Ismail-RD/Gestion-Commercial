package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.PaiementRequest;
import com.example.gestioncommerciale.dto.PaiementResponse;

import java.time.LocalDate;
import java.util.List;

public interface PaiementService {

    PaiementResponse enregistrer(PaiementRequest request);

    /** Remise en banque d'un effet. */
    PaiementResponse deposer(Long id, LocalDate dateRemise);

    /** Fonds credites : c'est ici que la facture est reellement soldee. */
    PaiementResponse encaisser(Long id, LocalDate dateEncaissement);

    /** Effet revenu impaye : le montant retombe et la facture redevient due. */
    PaiementResponse rejeter(Long id, String motif);

    /** Retire un paiement saisi par erreur, ou un effet rejete devenu inutile. */
    void supprimer(Long id);

    List<PaiementResponse> listerParFacture(Long factureId);

    /** Effets recus ou remis, en attente d'encaissement. */
    List<PaiementResponse> portefeuilleEffets();
}
