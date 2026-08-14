package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.Role;
import com.example.gestioncommerciale.entity.Utilisateur;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Unicite de l'adresse a la modification, en s'ignorant soi-meme. */
    boolean existsByEmailAndIdNot(String email, Long id);

    /** Retrouve l'invite a partir du jeton present dans son lien. */
    Optional<Utilisateur> findByTokenInvitation(String tokenInvitation);

    /** Comptes crees dont l'invitation n'a pas encore ete suivie. */
    List<Utilisateur> findByActifFalseAndTokenInvitationIsNotNull(Sort sort);

    List<Utilisateur> findByRole(Role role, Sort sort);
}
