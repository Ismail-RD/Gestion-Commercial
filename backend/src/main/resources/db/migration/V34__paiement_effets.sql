-- Un paiement ne portait qu'une date : le cas des especes. Un cheque, lui,
-- traverse un cycle -- recu, remis en banque, encaisse ou rejete -- et l'argent
-- n'arrive qu'au bout. Le compter des sa reception faisait afficher "payee" a
-- des factures qui ne l'etaient pas, et liberait le plafond de credit du client
-- sur un cheque qui pouvait revenir impaye.
alter table paiements
    add column if not exists statut            varchar(255),
    add column if not exists numero_effet      varchar(255),
    add column if not exists banque_emettrice  varchar(255),
    add column if not exists date_reception    date,
    add column if not exists date_echeance     date,
    add column if not exists date_remise       date,
    add column if not exists date_encaissement date,
    add column if not exists motif_rejet       text;

-- Les paiements deja saisis ont ete comptes dans le montant paye des factures :
-- ils sont donc encaisses, quel que soit leur mode.
update paiements set statut = 'ENCAISSE' where statut is null;
alter table paiements alter column statut set not null;
