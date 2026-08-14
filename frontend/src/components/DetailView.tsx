import { type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Divider,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';

/**
 * Briques d'affichage communes aux pages de détail (produit, client, fournisseur).
 * Centralise la mise en page pour que toutes les fiches se ressemblent.
 */

/** Un couple libelle / valeur. Rend "-" quand la valeur est absente. */
export function Champ({ label, valeur }: { label: string; valeur?: ReactNode }) {
  const vide = valeur === null || valeur === undefined || valeur === '';
  return (
    <Box sx={{ flex: '1 1 45%', minWidth: 220 }}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body1">{vide ? '-' : valeur}</Typography>
    </Box>
  );
}

/** Conteneur qui dispose les champs sur deux colonnes responsives. */
export function Champs({ children }: { children: ReactNode }) {
  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>{children}</Box>
  );
}

/** Un bloc titre + contenu, separe des autres. */
export function Section({ titre, children }: { titre: string; children: ReactNode }) {
  return (
    <Paper sx={{ p: 3, mb: 2 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>
        {titre}
      </Typography>
      {children}
    </Paper>
  );
}

/**
 * Cadre d'une page de détail : bouton retour, titre, gestion chargement/erreur.
 */
export function DetailLayout({
  titre,
  sousTitre,
  retour,
  isLoading,
  isError,
  actions,
  children,
}: {
  titre: string;
  sousTitre?: ReactNode;
  retour: string;
  isLoading?: boolean;
  isError?: boolean;
  actions?: ReactNode;
  children: ReactNode;
}) {
  const navigate = useNavigate();

  return (
    <Box>
      <Stack
        direction="row"
        sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}
      >
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
          <Button startIcon={<ArrowBackIcon />} onClick={() => navigate(retour)}>
            Retour
          </Button>
          <Divider orientation="vertical" flexItem />
          <Box>
            <Typography variant="h4">{titre}</Typography>
            {sousTitre && (
              <Typography variant="body2" color="text.secondary">
                {sousTitre}
              </Typography>
            )}
          </Box>
        </Stack>
        {actions}
      </Stack>

      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 6 }}>
          <CircularProgress />
        </Box>
      )}
      {isError && <Alert severity="error">Élément introuvable ou erreur de chargement</Alert>}
      {!isLoading && !isError && children}
    </Box>
  );
}
