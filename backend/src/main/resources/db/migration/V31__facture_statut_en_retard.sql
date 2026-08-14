-- EN_RETARD figurait dans l'enum sans que rien ne le pose : une facture echue
-- restait EMISE. Le statut devient fonctionnel, il faut donc rattraper les
-- factures dont l'echeance est deja passee.
update factures
   set statut = 'EN_RETARD'
 where statut in ('EMISE', 'PARTIELLEMENT_PAYEE')
   and date_echeance < current_date
   and montantttc > montant_paye;
