package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByDestinataireId(Long destinataireId, Pageable pageable);

    long countByDestinataireIdAndDateLectureIsNull(Long destinataireId);

    boolean existsByDestinataireIdAndCle(Long destinataireId, String cle);

    /**
     * Marque tout comme lu en une requete : charger puis sauver une a une
     * ferait autant d'ordres SQL que de notifications en attente.
     */
    @Modifying
    @Query("update Notification n set n.dateLecture = :maintenant "
            + "where n.destinataire.id = :destinataireId and n.dateLecture is null")
    int marquerToutLu(@Param("destinataireId") Long destinataireId,
                      @Param("maintenant") LocalDateTime maintenant);
}
