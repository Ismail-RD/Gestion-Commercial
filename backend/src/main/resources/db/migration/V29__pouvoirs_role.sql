-- La table ne portait que le seuil de remise. Elle accueille maintenant tout ce
-- qu'un role accorde seul, a commencer par le plafond de credit qu'il peut
-- consentir a un client : meme logique, meme place.
alter table seuils_remise rename to pouvoirs_role;
alter table pouvoirs_role rename column pourcentage to seuil_remise_pct;

-- Null = pas de plafond a accorder. Le commercial ne fixe pas les plafonds de
-- credit, la colonne reste vide pour lui.
alter table pouvoirs_role add column if not exists plafond_credit_max numeric(15, 2);

update pouvoirs_role set plafond_credit_max = 100000
 where role = 'RESPONSABLE_COMMERCIAL';
