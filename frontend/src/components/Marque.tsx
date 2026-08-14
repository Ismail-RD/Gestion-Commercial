import { Box, Typography } from '@mui/material';
import logo from '../assets/sogetherm.png';
import { BLEU_MARQUE } from '../theme';

/**
 * Bloc d'identité : le logo de l'entreprise, et sous lui le service auquel
 * l'application est destinée.
 *
 * <p>Le trait dégradé qui sépare les deux reprend les couleurs du pictogramme.
 * Il évite le filet gris habituel, qui aurait fait « séparateur de formulaire »
 * là où il s'agit d'une signature.
 */
export default function Marque({ taille = 'normale' }: { taille?: 'normale' | 'grande' }) {
  const grande = taille === 'grande';
  return (
    <Box sx={{ textAlign: 'center', px: 2, py: grande ? 3 : 2 }}>
      <Box
        component="img"
        src={logo}
        alt="SOGETHERM"
        sx={{ width: '100%', maxWidth: grande ? 280 : 168, display: 'block', mx: 'auto' }}
      />
      <Box
        sx={{
          height: 3,
          width: grande ? 120 : 72,
          mx: 'auto',
          my: grande ? 1.5 : 1,
          borderRadius: 2,
          backgroundImage: `linear-gradient(90deg, #F1662A 0%, #2BB673 50%, ${BLEU_MARQUE} 100%)`,
        }}
      />
      <Typography
        variant={grande ? 'subtitle1' : 'caption'}
        sx={{
          display: 'block',
          color: 'text.secondary',
          fontWeight: 600,
          letterSpacing: '0.18em',
          textTransform: 'uppercase',
        }}
      >
        Service commercial
      </Typography>
    </Box>
  );
}
