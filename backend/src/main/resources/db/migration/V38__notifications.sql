-- Notifications : un evenement adresse a une personne, non lu tant qu'elle ne
-- l'a pas ouvert. Le document concerne est designe par un couple (type, id) et
-- non par une cle etrangere, pour qu'une notification survive a la suppression
-- de son document.

CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    destinataire_id BIGINT       NOT NULL REFERENCES utilisateurs (id) ON DELETE CASCADE,
    type            VARCHAR(40)  NOT NULL,
    niveau          VARCHAR(20)  NOT NULL,
    titre           VARCHAR(120) NOT NULL,
    message         VARCHAR(255),
    type_document   VARCHAR(30),
    document_id     BIGINT,
    date_creation   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_lecture    TIMESTAMP,
    cle             VARCHAR(120)
);

-- La cloche lit toujours "mes notifications, les plus recentes d'abord".
CREATE INDEX idx_notifications_destinataire
    ON notifications (destinataire_id, date_creation DESC);

-- Idempotence des alertes recurrentes : la meme alerte, pour la meme personne,
-- sur la meme periode, ne peut pas etre inseree deux fois. La contrainte vit en
-- base parce qu'une verification en Java laisserait passer les doublons dans
-- une execution concurrente.
CREATE UNIQUE INDEX idx_notifications_cle
    ON notifications (destinataire_id, cle) WHERE cle IS NOT NULL;
