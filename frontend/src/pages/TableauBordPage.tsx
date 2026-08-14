import { Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Chip,
  Divider,
  Grid,
  LinearProgress,
  Link,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useAuth } from '../auth/AuthContext';
import { monTableauBord, type Ton } from '../api/tableauBord';
import SectionVisionStock from '../components/SectionVisionStock';
import Graphe from '../components/Graphe';
import { BLEU_MARQUE } from '../theme';

/** Le ton porte la couleur, jamais le sens : le libelle dit deja ce qu il faut lire. */
const COULEURS: Record<Ton, string> = {
  neutre: 'text.primary',
  succes: 'success.main',
  attention: 'warning.main',
  alerte: 'error.main',
};

const COULEURS_CHIP: Record<Ton, 'default' | 'success' | 'warning' | 'error'> = {
  neutre: 'default',
  succes: 'success',
  attention: 'warning',
  alerte: 'error',
};

export default function TableauBordPage() {
  const { user } = useAuth();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['tableau-de-bord'],
    queryFn: monTableauBord,
  });

  return (
    <Box>
      {isLoading && <LinearProgress />}
      {isError && (
        <Alert severity="error">Le tableau de bord n'a pas pu être chargé.</Alert>
      )}

      {data && (
        <Stack spacing={3}>
          {/* Bandeau d'accueil : il nomme la personne avant de lui parler travail. */}
          <Paper
            sx={{
              p: 3,
              color: 'common.white',
              backgroundImage: `linear-gradient(120deg, ${BLEU_MARQUE} 0%, #0284C7 55%, #075985 100%)`,
            }}
          >
            <Typography variant="h4">{data.titre}</Typography>
            <Typography variant="body1" sx={{ opacity: 0.9, mt: 0.5 }}>
              Bonjour {user?.prenom}, {data.sousTitre.charAt(0).toLowerCase() + data.sousTitre.slice(1)}
            </Typography>
          </Paper>

          {/* Les chiffres qui situent */}
          <Grid container spacing={2}>
            {data.indicateurs.map((i) => (
              <Grid key={i.libelle} size={{ xs: 12, sm: 6, md: 3 }}>
                <Paper
                  sx={{
                    p: 2.5, height: '100%', position: 'relative', overflow: 'hidden',
                    // Un filet coloré à gauche : le ton se voit avant le chiffre.
                    '&::before': {
                      content: '""', position: 'absolute', left: 0, top: 0, bottom: 0, width: 4,
                      backgroundColor: COULEURS[i.ton] === 'text.primary'
                        ? BLEU_MARQUE : undefined,
                      bgcolor: COULEURS[i.ton] === 'text.primary'
                        ? BLEU_MARQUE : COULEURS[i.ton],
                    },
                  }}
                >
                  <Typography
                    variant="caption"
                    sx={{
                      color: 'text.secondary', fontWeight: 600,
                      letterSpacing: '0.04em', textTransform: 'uppercase',
                    }}
                  >
                    {i.libelle}
                  </Typography>
                  {/* Un montant à sept chiffres tient sur une ligne en h5, pas en h4. */}
                  <Typography variant="h5" sx={{ mt: 0.5, color: COULEURS[i.ton] }}>
                    {i.valeur}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">{i.detail}</Typography>
                </Paper>
              </Grid>
            ))}
          </Grid>

          {/* Les files d'attente : ce qu'il y a à faire */}
          {data.files.length === 0 ? (
            <Alert severity="success">
              Vous êtes à jour : rien n'attend d'être traité.
            </Alert>
          ) : (
            <Grid container spacing={3}>
              {data.files.map((f) => (
                <Grid key={f.titre} size={{ xs: 12, md: 6 }}>
                  <Paper sx={{ p: 3, height: '100%' }}>
                    <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <Typography variant="h6">{f.titre}</Typography>
                      <Chip size="small" label={f.total} />
                    </Stack>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, mb: 1.5 }}>
                      {f.description}
                    </Typography>
                    <Divider />
                    <Stack divider={<Divider />}>
                      {f.elements.map((e, index) => (
                        <Link
                          key={`${e.titre}-${index}`}
                          component={RouterLink}
                          to={e.lien}
                          underline="none"
                          color="inherit"
                          sx={{ py: 1.2, '&:hover': { bgcolor: 'action.hover' } }}
                        >
                          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                            <Box sx={{ minWidth: 0 }}>
                              <Typography variant="body2" sx={{ color: COULEURS_CHIP[e.ton] === 'default' ? 'text.primary' : COULEURS[e.ton] }}>
                                <strong>{e.titre}</strong>
                              </Typography>
                              <Typography variant="caption" color="text.secondary" noWrap>
                                {e.sousTitre}
                              </Typography>
                            </Box>
                            <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', flexShrink: 0 }}>
                              <Typography variant="caption" color="text.secondary">{e.info}</Typography>
                              <ChevronRightIcon fontSize="small" color="disabled" />
                            </Stack>
                          </Stack>
                        </Link>
                      ))}
                    </Stack>
                    {/* Les premiers seulement : le lien mène au reste */}
                    {f.total > f.elements.length && (
                      <Link component={RouterLink} to={f.lien} variant="body2" sx={{ display: 'inline-block', mt: 1.5 }}>
                        Voir les {f.total} éléments
                      </Link>
                    )}
                  </Paper>
                </Grid>
              ))}
            </Grid>
          )}

          {/* La lecture graphique du rôle */}
          {data.visuel && data.visuel.barres.length > 0 && (
            <Paper sx={{ p: 3 }}>
              <Typography variant="h6">{data.visuel.titre}</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, mb: 3 }}>
                {data.visuel.description}
              </Typography>
              <Graphe forme={data.visuel.forme} barres={data.visuel.barres} />
            </Paper>
          )}

          {/* L'entrepot merite sa vision d'ensemble sous sa file de travail */}
          {data.role === 'MAGASINIER' && (
            <>
              <Divider sx={{ pt: 1 }} />
              <SectionVisionStock />
            </>
          )}
        </Stack>
      )}
    </Box>
  );
}
