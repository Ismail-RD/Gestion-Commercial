-- L'aval de l'encadrement sur une remise excessive ne fait plus avancer le
-- devis : il debloque l'envoi, que le commercial declenche lui-meme. Il faut
-- donc memoriser l'accord, le statut ne le porte plus.
alter table devis add column if not exists remise_validee boolean not null default false;

-- Les devis deja sortis du brouillon ont franchi le controle a l'epoque : les
-- marquer valides evite de bloquer retroactivement leur impression et leur envoi.
update devis set remise_validee = true
 where statut not in ('BROUILLON', 'EN_ATTENTE_VALIDATION');
