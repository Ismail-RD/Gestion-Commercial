import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Chip,
  Grid,
  LinearProgress,
  Link,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import WarehouseIcon from '@mui/icons-material/Warehouse';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlined';
import LockClockIcon from '@mui/icons-material/LockClock';
import BedtimeIcon from '@mui/icons-material/Bedtime';
import { tableauBordStock } from '../api/tableauBord';
import { formatMontant } from '../utils/format';

const FENETRES = [
  { valeur: 30, libelle: '30 jours' },
  { valeur: 90, libelle: '90 jours' },
  { valeur: 180, libelle: '6 mois' },
  { valeur: 365, libelle: '1 an' },
];

const formatQuantite = (q: number) =>
  new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 }).format(q);

/**
 * Vision d ensemble du stock. Vit sous la file de travail du magasinier : elle
 * ne se lit pas seule, elle eclaire les commandes qu il a a servir.
 */
export default function SectionVisionStock() {
  const [jours, setJours] = useState(90);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['tableau-bord-stock', jours],
    queryFn: () => tableauBordStock(jours),
  });

  return (
    <Box>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h5">Vision du stock</Typography>
        <TextField
          select
          size="small"
          label="Période observee"
          value={jours}
          onChange={(e) => setJours(Number(e.target.value))}
          sx={{ width: 180 }}
        >
          {FENETRES.map((f) => (
            <MenuItem key={f.valeur} value={f.valeur}>{f.libelle}</MenuItem>
          ))}
        </TextField>
      </Stack>

      {isLoading && <LinearProgress />}
      {isError && <Alert severity="error">Erreur de chargement du tableau de bord</Alert>}

      {data && (
        <Stack spacing={3}>
          {/* Les quatre chiffres qui resument la situation */}
          <Grid container spacing={2}>
            <Tuile
              titre="Valeur du stock"
              valeur={formatMontant(data.valeur.totale)}
              detail={`dont ${formatMontant(data.valeur.reservee)} reserves`}
              icone={<WarehouseIcon />}
              couleur="primary.main"
            />
            <Tuile
              titre="Disponible a la vente"
              valeur={formatMontant(data.valeur.disponible)}
              detail={`${data.compteurs.referencesEnStock} références en stock`}
              icone={<WarehouseIcon />}
              couleur="success.main"
            />
            <Tuile
              titre="Ruptures"
              valeur={String(data.compteurs.ruptures)}
              detail={`dont ${data.compteurs.toutReserve} entierement reserves`}
              icone={<ErrorOutlineIcon />}
              couleur="error.main"
            />
            <Tuile
              titre="Dormants"
              valeur={String(data.compteurs.dormants)}
              detail={`aucune sortie depuis ${data.jours} jours`}
              icone={<BedtimeIcon />}
              couleur="warning.main"
            />
          </Grid>

          <Grid container spacing={3}>
            {/* Ou est l'argent */}
            <Grid size={{ xs: 12, md: 6 }}>
              <Paper sx={{ p: 3, height: '100%' }}>
                <Typography variant="h6" sx={{ mb: 2 }}>Répartition par dépôt</Typography>
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Dépôt</TableCell>
                        <TableCell align="right">Quantité</TableCell>
                        <TableCell align="right">Réservée</TableCell>
                        <TableCell align="right">Valeur</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {data.parDepot.map((d) => (
                        <TableRow key={d.depotCode}>
                          <TableCell><Chip size="small" label={d.depotCode} /></TableCell>
                          <TableCell align="right">{formatQuantite(d.quantite)}</TableCell>
                          <TableCell align="right">{formatQuantite(d.quantiteReservee)}</TableCell>
                          <TableCell align="right">{formatMontant(d.valeur)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Paper>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <Paper sx={{ p: 3, height: '100%' }}>
                <Typography variant="h6" sx={{ mb: 2 }}>Valeur par catégorie</Typography>
                <Stack spacing={1.5}>
                  {data.parCategorie.map((c) => (
                    <Box key={c.categorie}>
                      <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                        <Typography variant="body2">{c.categorie}</Typography>
                        <Typography variant="body2"><strong>{formatMontant(c.valeur)}</strong></Typography>
                      </Stack>
                      <LinearProgress
                        variant="determinate"
                        value={part(c.valeur, data.valeur.totale)}
                        sx={{ height: 8, borderRadius: 1 }}
                      />
                    </Box>
                  ))}
                  {data.parCategorie.length === 0 && (
                    <Typography variant="body2" color="text.secondary">
                      Aucun stock enregistre.
                    </Typography>
                  )}
                </Stack>
              </Paper>
            </Grid>
          </Grid>

          {/* La decision la plus immediate : servir un depot depuis un autre */}
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6">Transferts possibles</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, mb: 2 }}>
              Un depot est a sec pendant qu'un autre a de quoi servir. Ni achat, ni delai.
            </Typography>
            {data.transferts.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                Aucun transfert a proposer.
              </Typography>
            ) : (
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Produit</TableCell>
                      <TableCell>Dépôt a sec</TableCell>
                      <TableCell>À prendre dans</TableCell>
                      <TableCell align="right">Disponible</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.transferts.map((t) => (
                      <TableRow key={`${t.produitId}-${t.depotDemandeur}`}>
                        <TableCell><LienProduit id={t.produitId} reference={t.reference} designation={t.designation} /></TableCell>
                        <TableCell><Chip size="small" color="error" label={t.depotDemandeur} /></TableCell>
                        <TableCell><Chip size="small" color="success" label={t.depotFournisseur} /></TableCell>
                        <TableCell align="right">{formatQuantite(t.disponibleChezFournisseur)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Paper>

          <Grid container spacing={3}>
            {/* Ce qui manque */}
            <Grid size={{ xs: 12, md: 6 }}>
              <Paper sx={{ p: 3, height: '100%' }}>
                <Typography variant="h6">Ruptures</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, mb: 2 }}>
                  Plus rien de vendable. La pastille distingue le stock absent de celui
                  qui est present mais entierement promis.
                </Typography>
                <ListeVide vide={data.ruptures.length === 0} message="Aucune rupture.">
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Produit</TableCell>
                        <TableCell align="right">Physique</TableCell>
                        <TableCell align="right">Réservé</TableCell>
                        <TableCell />
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {data.ruptures.map((r) => (
                        <TableRow key={r.produitId}>
                          <TableCell><LienProduit id={r.produitId} reference={r.reference} designation={r.designation} /></TableCell>
                          <TableCell align="right">{formatQuantite(r.quantite)}</TableCell>
                          <TableCell align="right">{formatQuantite(r.quantiteReservee)}</TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              icon={r.toutReserve ? <LockClockIcon /> : <ErrorOutlineIcon />}
                              color={r.toutReserve ? 'warning' : 'error'}
                              label={r.toutReserve ? 'Tout reserve' : 'Vide'}
                            />
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </ListeVide>
              </Paper>
            </Grid>

            {/* Ce qui dort */}
            <Grid size={{ xs: 12, md: 6 }}>
              <Paper sx={{ p: 3, height: '100%' }}>
                <Typography variant="h6">Stock dormant</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, mb: 2 }}>
                  De l'argent immobilise qui n'est pas sorti sur la periode. Trie par
                  montant : le haut de la liste coute le plus cher.
                </Typography>
                <ListeVide vide={data.dormants.length === 0} message="Tout le stock a bouge.">
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Produit</TableCell>
                        <TableCell align="right">Quantité</TableCell>
                        <TableCell align="right">Immobilisé</TableCell>
                        <TableCell align="right">Dernière sortie</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {data.dormants.map((d) => (
                        <TableRow key={d.produitId}>
                          <TableCell><LienProduit id={d.produitId} reference={d.reference} designation={d.designation} /></TableCell>
                          <TableCell align="right">{formatQuantite(d.quantite)}</TableCell>
                          <TableCell align="right">{formatMontant(d.valeurImmobilisee)}</TableCell>
                          <TableCell align="right">
                            {d.joursDepuisDerniereSortie === null
                              ? <Tooltip title="Ce produit n'est jamais sorti"><span>jamais</span></Tooltip>
                              : `il y a ${d.joursDepuisDerniereSortie} j`}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </ListeVide>
              </Paper>
            </Grid>
          </Grid>

          <Grid container spacing={3}>
            {/* Ce qui tourne */}
            <Grid size={{ xs: 12, md: 6 }}>
              <Paper sx={{ p: 3, height: '100%' }}>
                <Typography variant="h6" sx={{ mb: 1 }}>Ce qui tourne le plus</Typography>
                <Stack direction="row" spacing={3} sx={{ mb: 2 }}>
                  <Indicateur libelle="Entrees" valeur={formatQuantite(data.flux.entrees)} />
                  <Indicateur libelle="Sorties" valeur={formatQuantite(data.flux.sorties)} />
                  <Indicateur libelle="Ajustements" valeur={formatQuantite(data.flux.ajustements)} />
                  <Indicateur libelle="Mouvements" valeur={String(data.flux.nombreMouvements)} />
                </Stack>
                <ListeVide vide={data.rotations.length === 0} message="Aucune sortie sur la période.">
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Produit</TableCell>
                        <TableCell align="right">Quantité sortie</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {data.rotations.map((r) => (
                        <TableRow key={r.produitId}>
                          <TableCell><LienProduit id={r.produitId} reference={r.reference} designation={r.designation} /></TableCell>
                          <TableCell align="right">{formatQuantite(r.quantiteSortie)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </ListeVide>
              </Paper>
            </Grid>

            {/* Les corrections d'inventaire */}
            <Grid size={{ xs: 12, md: 6 }}>
              <Paper sx={{ p: 3, height: '100%' }}>
                <Typography variant="h6">Corrections d'inventaire</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, mb: 2 }}>
                  Repetees sur un meme produit, elles signalent une casse, un vol ou un
                  comptage douteux.
                </Typography>
                <ListeVide vide={data.derniersAjustements.length === 0} message="Aucun ajustement sur la période.">
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Date</TableCell>
                        <TableCell>Produit</TableCell>
                        <TableCell>Dépôt</TableCell>
                        <TableCell align="right">Quantité</TableCell>
                        <TableCell>Par</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {data.derniersAjustements.map((a, i) => (
                        <TableRow key={`${a.date}-${i}`}>
                          <TableCell>{new Date(a.date).toLocaleDateString('fr-FR')}</TableCell>
                          <TableCell>
                            <Tooltip title={a.motif ?? ''}>
                              <span>{a.reference}</span>
                            </Tooltip>
                          </TableCell>
                          <TableCell>{a.depotCode}</TableCell>
                          <TableCell align="right">{formatQuantite(a.quantite)}</TableCell>
                          <TableCell>{a.utilisateur}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </ListeVide>
              </Paper>
            </Grid>
          </Grid>

          {data.compteurs.jamaisEntrees > 0 && (
            <Alert severity="info">
              {data.compteurs.jamaisEntrees} reference(s) du catalogue ne sont jamais
              entrees en stock. Elles ne comptent pas parmi les ruptures : elles n'ont
              simplement jamais ete recues.
            </Alert>
          )}
        </Stack>
      )}
    </Box>
  );
}

function part(valeur: number, total: number) {
  return total > 0 ? Math.min((valeur / total) * 100, 100) : 0;
}

function Tuile({ titre, valeur, detail, icone, couleur }: {
  titre: string; valeur: string; detail: string; icone: React.ReactNode; couleur: string;
}) {
  return (
    <Grid size={{ xs: 12, sm: 6, md: 3 }}>
      <Paper sx={{ p: 2.5 }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', color: couleur }}>
          {icone}
          <Typography variant="body2" color="text.secondary">{titre}</Typography>
        </Stack>
        <Typography variant="h5" sx={{ mt: 1 }}>{valeur}</Typography>
        <Typography variant="caption" color="text.secondary">{detail}</Typography>
      </Paper>
    </Grid>
  );
}

function Indicateur({ libelle, valeur }: { libelle: string; valeur: string }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">{libelle}</Typography>
      <Typography variant="subtitle1"><strong>{valeur}</strong></Typography>
    </Box>
  );
}

/** Evite de repeter le message "rien a signaler" dans chaque bloc. */
function ListeVide({ vide, message, children }: {
  vide: boolean; message: string; children: React.ReactNode;
}) {
  if (vide) {
    return <Typography variant="body2" color="text.secondary">{message}</Typography>;
  }
  return <TableContainer>{children}</TableContainer>;
}

function LienProduit({ id, reference, designation }: {
  id: number; reference: string; designation: string;
}) {
  return (
    <Link component={RouterLink} to={`/produits/${id}`} underline="hover">
      <strong>{reference}</strong> · {designation}
    </Link>
  );
}
