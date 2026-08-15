import type { ReactNode } from 'react';
import { Box, Stack, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { BLEU_MARQUE } from '../theme';

/**
 * Titre d'une page, précédé de l'icône de sa rubrique.
 *
 * <p>La pastille reprend l'icône du menu : arrivé sur une page, on retrouve le
 * même signe qu'on vient de cliquer, ce qui vaut mieux qu'un titre nu quand on
 * navigue vite entre une dizaine d'écrans qui se ressemblent.
 *
 * <p>Le fond de la pastille est un bleu très dilué, jamais un aplat plein :
 * l'icône doit se remarquer sans concurrencer le bouton d'action, qui reste la
 * seule tache de couleur pleine de la ligne.
 */
export default function EnTetePage({
  titre,
  icone,
  sousTitre,
}: {
  titre: string;
  icone: ReactNode;
  /** Une ligne pour dire à quoi sert la page, quand ce n'est pas évident. */
  sousTitre?: string;
}) {
  return (
    <Stack direction="row" spacing={1.75} sx={{ alignItems: 'center', minWidth: 0 }}>
      <Box
        sx={{
          width: 46, height: 46, borderRadius: 3, flexShrink: 0,
          display: 'grid', placeItems: 'center',
          color: 'primary.main',
          bgcolor: alpha(BLEU_MARQUE, 0.14),
          '& .MuiSvgIcon-root': { fontSize: 26 },
        }}
      >
        {icone}
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="h4">{titre}</Typography>
        {sousTitre && (
          <Typography variant="body2" color="text.secondary">
            {sousTitre}
          </Typography>
        )}
      </Box>
    </Stack>
  );
}
