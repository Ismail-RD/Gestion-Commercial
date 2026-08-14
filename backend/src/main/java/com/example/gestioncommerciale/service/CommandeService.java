package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.CommandeRequest;
import com.example.gestioncommerciale.dto.CommandeResponse;
import com.example.gestioncommerciale.dto.ModificationLignesRequest;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.ValidationCommandeRequest;
import com.example.gestioncommerciale.dto.filter.CommandeFilter;
import org.springframework.data.domain.Pageable;

public interface CommandeService {

    /** Commande saisie directement, sans devis prealable. */
    CommandeResponse creer(CommandeRequest request);

    CommandeResponse creerDepuisDevis(Long devisId);

    /**
     * Met a jour une commande : ses lignes, et son client tant que rien ne
     * l'interdit (pas de devis d'origine et commande encore EN_ATTENTE).
     */
    CommandeResponse modifier(Long id, CommandeRequest request);

    CommandeResponse trouverParId(Long id);

    PageResponse<CommandeResponse> lister(CommandeFilter filtre, Pageable pageable);

    /**
     * Valide une commande EN_ATTENTE : verifie et decremente le stock du depot
     * choisi pour chaque ligne, puis passe la commande a VALIDEE.
     */
    CommandeResponse valider(Long id, ValidationCommandeRequest request);

    /**
     * Redefinit les lignes d'une commande encore EN_ATTENTE : suppression de
     * lignes et ajout de produits issus du devis d'origine. Les reservations de
     * stock et les montants sont recalcules en consequence.
     */
    CommandeResponse modifierLignes(Long id, ModificationLignesRequest request);

    CommandeResponse changerStatut(Long id, String statut);

    /** Aval de l'encadrement sur une remise excessive : la commande repart en EN_ATTENTE. */
    CommandeResponse validerRemise(Long id);

    void supprimer(Long id);
}
