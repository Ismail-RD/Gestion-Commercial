-- La date d'emission manquait : c'est celle portee sur le cheque ou la traite
-- par celui qui l'etablit. Elle differe de la date ou l'entreprise le recoit,
-- et c'est elle qui fait foi en cas de litige.
alter table paiements add column if not exists date_emission date;
