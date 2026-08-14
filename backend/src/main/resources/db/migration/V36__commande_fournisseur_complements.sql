-- Complements sur les commandes fournisseur.
--
-- Le mode de transport conditionne les delais et la nature des frais ; le pays
-- d'origine determine les droits de douane et le certificat d'origine.
alter table commandes_fournisseur
    add column if not exists mode_transport varchar(255),
    add column if not exists pays_origine   varchar(255);

-- Fret et assurance sont souvent factures en devise par le transitaire, alors
-- que douane et transit sont toujours percus en dirhams. Ce drapeau dit dans
-- quelle monnaie lire les deux premiers ; ils sont convertis au taux du dossier.
alter table commandes_fournisseur
    add column if not exists frais_transport_en_devise boolean not null default false;
