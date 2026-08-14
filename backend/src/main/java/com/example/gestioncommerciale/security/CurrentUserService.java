package com.example.gestioncommerciale.security;

import com.example.gestioncommerciale.entity.Role;
import com.example.gestioncommerciale.entity.Utilisateur;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Expose l'utilisateur actuellement authentifie (issu du JWT) au reste de l'application.
 */
@Service
public class CurrentUserService {

    public Utilisateur getUtilisateurCourant() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UtilisateurDetails details) {
            return details.getUtilisateur();
        }
        throw new UsernameNotFoundException("Aucun utilisateur authentifie");
    }

    public boolean estAdmin() {
        return aRole(Role.ADMIN);
    }

    public boolean aRole(Role... roles) {
        Role courant = getUtilisateurCourant().getRole();
        for (Role role : roles) {
            if (courant == role) {
                return true;
            }
        }
        return false;
    }

    /**
     * Identifiant du commercial auquel l'utilisateur est cantonne, ou {@code null}
     * s'il voit l'ensemble du portefeuille. Seul le COMMERCIAL est restreint : il
     * ne doit acceder ni aux clients ni aux documents de ses collegues.
     */
    public Long restrictionAuCommercial() {
        Utilisateur courant = getUtilisateurCourant();
        return courant.getRole() == Role.COMMERCIAL ? courant.getId() : null;
    }

    /**
     * Vrai si l'utilisateur ne peut pas fixer lui-meme le prix et la TVA des
     * lignes : elles sont alors reprises du devis d'origine ou du catalogue.
     */
    public boolean prixImposes() {
        return aRole(Role.COMMERCIAL);
    }
}
