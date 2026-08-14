-- Le seuil de remise vivait dans application.properties : une seule valeur pour
-- tout le monde, et un redemarrage pour la changer. Il devient un parametre par
-- role, modifiable par l'administrateur depuis l'application.
create table if not exists seuils_remise (
    role        varchar(50)   primary key,
    pourcentage numeric(5, 2) not null
);

-- Le commercial garde la valeur qui etait configuree. Le responsable commercial
-- n'avait aucun plafond jusqu'ici : on lui en pose un, large, que l'admin
-- ajustera depuis l'ecran des parametres.
insert into seuils_remise (role, pourcentage) values
    ('COMMERCIAL', 20),
    ('RESPONSABLE_COMMERCIAL', 50)
on conflict (role) do nothing;

-- Un devis dont la remise n'attend pas d'arbitrage a franchi le controle sous
-- les regles precedentes : le marquer valide evite de bloquer retroactivement
-- son envoi et son impression.
update devis set remise_validee = true where statut <> 'EN_ATTENTE_VALIDATION';
