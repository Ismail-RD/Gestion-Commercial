import { useState } from 'react';
import { libelle, TYPE_MOUVEMENT } from '../utils/libelles';
import { useAuth } from '../auth/AuthContext';
import { droits } from '../auth/droits';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import {
  ajusterStock,
  apercuStock,
  entreeStock,
  listerMouvements,
  listerStock,
  sortieStock,
  transfertStock,
  type ApercuQuery,
  type MouvementQuery,
  type StockQuery,
} from '../api/stock';
import { listerProduits } from '../api/produits';
import { listerDepots } from '../api/depots';
import type { TypeMouvement } from '../api/types';

const MOUVEMENT_COLORS: Record<TypeMouvement, 'success' | 'error' | 'warning'> = {
  ENTREE: 'success',
  SORTIE: 'error',
  AJUSTEMENT: 'warning',
};

export default function StockPage() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const mesDroits = droits(user?.role);
  const [tab, setTab] = useState(0);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [depotFilter, setDepotFilter] = useState<string>('');
  const [typeFilter, setTypeFilter] = useState('');
  const [recherche, setRecherche] = useState('');

  // Dialogs
  const [entreeOpen, setEntreeOpen] = useState(false);
  const [sortieOpen, setSortieOpen] = useState(false);
  const [ajustOpen, setAjustOpen] = useState(false);
  const [transfertOpen, setTransfertOpen] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  // Form states
  const [produitId, setProduitId] = useState<number>(0);
  const [depotCode, setDepotCode] = useState<string>('SH');
  const [quantite, setQuantite] = useState<number>(1);
  const [motif, setMotif] = useState('');
  const [nouvelleQuantite, setNouvelleQuantite] = useState<number>(0);
  const [depotDest, setDepotDest] = useState<string>('AB');

  const stockQuery: StockQuery = {
    page,
    size,
    sort: 'depot.code,asc',
    recherche: recherche || undefined,
    depotCode: depotFilter || undefined,
  };

  const mouvementQuery: MouvementQuery = {
    page,
    size,
    sort: 'dateMouvement,desc',
    recherche: recherche || undefined,
    depotCode: depotFilter || undefined,
    type: (typeFilter as TypeMouvement) || undefined,
  };

  const apercuQuery: ApercuQuery = {
    page,
    size,
    sort: 'reference,asc',
    recherche: recherche || undefined,
  };

  const { data: apercuData, isLoading: apercuLoading, isError: apercuError } = useQuery({
    queryKey: ['stock-apercu', apercuQuery],
    queryFn: () => apercuStock(apercuQuery),
  });

  const { data: stockData, isLoading: stockLoading, isError: stockError } = useQuery({
    queryKey: ['stock', stockQuery],
    queryFn: () => listerStock(stockQuery),
  });

  const { data: mouvementData, isLoading: mouvLoading, isError: mouvError } = useQuery({
    queryKey: ['mouvements', mouvementQuery],
    queryFn: () => listerMouvements(mouvementQuery),
  });

  const { data: produits } = useQuery({
    queryKey: ['produits-list'],
    queryFn: () => listerProduits({ size: 200 }),
  });

  const { data: depots } = useQuery({
    queryKey: ['depots-list'],
    queryFn: listerDepots,
  });


  const entreeMutation = useMutation({
    mutationFn: entreeStock,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stock'] });
      queryClient.invalidateQueries({ queryKey: ['mouvements'] });
      setEntreeOpen(false);
      resetForms();
    },
    onError: () => setFormError('Erreur lors de l\'entrée en stock'),
  });

  const sortieMutation = useMutation({
    mutationFn: sortieStock,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stock'] });
      queryClient.invalidateQueries({ queryKey: ['mouvements'] });
      setSortieOpen(false);
      resetForms();
    },
    onError: () => setFormError('Erreur lors de la sortie de stock (stock insuffisant ?)'),
  });

  const ajustMutation = useMutation({
    mutationFn: ajusterStock,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stock'] });
      queryClient.invalidateQueries({ queryKey: ['mouvements'] });
      setAjustOpen(false);
      resetForms();
    },
    onError: () => setFormError("Erreur lors de l'ajustement"),
  });

  const transfertMutation = useMutation({
    mutationFn: transfertStock,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stock'] });
      queryClient.invalidateQueries({ queryKey: ['mouvements'] });
      setTransfertOpen(false);
      resetForms();
    },
    onError: () => setFormError('Erreur lors du transfert'),
  });

  const resetForms = () => {
    setProduitId(0);
    setDepotCode('SH');
    setQuantite(1);
    setMotif('');
    setNouvelleQuantite(0);
    setDepotDest('AB');
    setFormError(null);
  };

  const produitsList = produits?.content ?? [];
  const depotsList = depots ?? [];

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 2 }}>Stock</Typography>

      <Paper sx={{ mb: 2 }}>
        <Tabs value={tab} onChange={(_, v) => { setTab(v); setPage(0); }}>
          <Tab label="Vue par produit" />
          <Tab label="Niveaux de stock" />
          <Tab label="Mouvements" />
        </Tabs>
      </Paper>

      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
          <TextField
            label="Rechercher"
            placeholder="Référence, désignation, catégorie, dépôt…"
            size="small"
            value={recherche}
            onChange={(e) => { setPage(0); setRecherche(e.target.value); }}
            sx={{ flex: 1, minWidth: 280 }}
          />
          {tab !== 0 && (
            <TextField
              label="Dépôt"
              select
              size="small"
              value={depotFilter}
              onChange={(e) => { setPage(0); setDepotFilter(e.target.value); }}
              sx={{ width: 200 }}
            >
              <MenuItem value="">Tous</MenuItem>
              {depotsList.map((d) => (
                <MenuItem key={d.id} value={d.code}>Depot {d.code}</MenuItem>
              ))}
            </TextField>
          )}
          {tab === 2 && (
            <TextField
              label="Type mouvement"
              select
              size="small"
              value={typeFilter}
              onChange={(e) => { setPage(0); setTypeFilter(e.target.value); }}
              sx={{ width: 200 }}
            >
              <MenuItem value="">Tous</MenuItem>
              <MenuItem value="ENTREE">Entrée</MenuItem>
              <MenuItem value="SORTIE">Sortie</MenuItem>
              <MenuItem value="AJUSTEMENT">Ajustement</MenuItem>
            </TextField>
          )}
          <Box sx={{ flexGrow: 1 }} />
          {/* Mouvements de stock : metier du magasinier, invisibles pour les autres */}
          {mesDroits.ecrireStock && (
            <>
              <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEntreeOpen(true)}>
                Entree
              </Button>
              <Button variant="outlined" color="error" onClick={() => setSortieOpen(true)}>
                Sortie
              </Button>
              <Button variant="outlined" onClick={() => setAjustOpen(true)}>
                Ajustement
              </Button>
              <Button variant="outlined" startIcon={<ArrowForwardIcon />} onClick={() => setTransfertOpen(true)}>
                Transfert
              </Button>
            </>
          )}
        </Stack>
      </Paper>

      {/* Onglet vue par produit : tout le catalogue, meme sans stock */}
      {tab === 0 && (
        <>
          {apercuError && <Alert severity="error">Erreur de chargement de l'aperçu</Alert>}
          <TableContainer component={Paper}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Produit</TableCell>
                  <TableCell>Catégorie</TableCell>
                  {depotsList.map((d) => (
                    <TableCell key={d.id} align="center">{d.code}</TableCell>
                  ))}
                  <TableCell align="center"><strong>Total</strong></TableCell>
                  <TableCell align="center">Réservé</TableCell>
                  <TableCell align="center"><strong>Disponible</strong></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {apercuLoading && (
                  <TableRow>
                    <TableCell colSpan={depotsList.length + 5} align="center">Chargement...</TableCell>
                  </TableRow>
                )}
                {apercuData?.content.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={depotsList.length + 5} align="center">Aucun produit</TableCell>
                  </TableRow>
                )}
                {apercuData?.content.map((a) => (
                  <TableRow key={a.produitId} hover>
                    <TableCell>
                      <strong>{a.reference}</strong>
                      <br />
                      <Typography variant="caption" color="text.secondary">
                        {a.designation}
                      </Typography>
                    </TableCell>
                    <TableCell>{a.categorieNom ?? '-'}</TableCell>
                    {a.depots.map((d) => (
                      <TableCell key={d.depotCode} align="center">
                        <Typography
                          variant="body2"
                          color={d.quantite === 0 ? 'text.disabled' : 'text.primary'}
                        >
                          {d.quantite}
                        </Typography>
                        {/* Part promise a une commande validee, encore en depot */}
                        {!!d.quantiteReservee && (
                          <Typography variant="caption" color="warning.main">
                            dont {d.quantiteReservee} res.
                          </Typography>
                        )}
                      </TableCell>
                    ))}
                    <TableCell align="center">
                      <Chip
                        label={a.stockTotal}
                        size="small"
                        color={a.stockTotal === 0 ? 'error' : a.stockTotal < 10 ? 'warning' : 'success'}
                      />
                    </TableCell>
                    <TableCell align="center">
                      <Typography variant="body2" color={a.quantiteReservee ? 'warning.main' : 'text.disabled'}>
                        {a.quantiteReservee ?? 0}
                      </Typography>
                    </TableCell>
                    <TableCell align="center">
                      <Chip
                        label={a.disponible ?? a.stockTotal}
                        size="small"
                        variant="outlined"
                        color={(a.disponible ?? a.stockTotal) <= 0 ? 'error' : 'success'}
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={apercuData?.totalElements ?? 0}
              page={page}
              onPageChange={(_, newPage) => setPage(newPage)}
              rowsPerPage={size}
              onRowsPerPageChange={(e) => { setSize(parseInt(e.target.value, 10)); setPage(0); }}
              rowsPerPageOptions={[10, 25, 50, 100]}
              labelRowsPerPage="Lignes par page"
            />
          </TableContainer>
        </>
      )}

      {/* Onglet niveaux de stock */}
      {tab === 1 && (
        <>
          {stockError && <Alert severity="error">Erreur de chargement du stock</Alert>}
          <TableContainer component={Paper}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Dépôt</TableCell>
                  <TableCell>Produit</TableCell>
                  <TableCell align="right">Quantité</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {stockLoading && (
                  <TableRow>
                    <TableCell colSpan={3} align="center">Chargement...</TableCell>
                  </TableRow>
                )}
                {stockData?.content.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={3} align="center">Aucune donnée de stock</TableCell>
                  </TableRow>
                )}
                {stockData?.content.map((s) => (
                  <TableRow key={s.id} hover>
                    <TableCell>Depot {s.depotCode}</TableCell>
                    <TableCell>
                      <strong>{s.produitReference}</strong>
                      <br />
                      <Typography variant="caption" color="text.secondary">
                        {s.produitDesignation}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Chip label={s.quantite} color={s.quantite === 0 ? 'error' : s.quantite < 10 ? 'warning' : 'success'} size="small" />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={stockData?.totalElements ?? 0}
              page={page}
              onPageChange={(_, newPage) => setPage(newPage)}
              rowsPerPage={size}
              onRowsPerPageChange={(e) => { setSize(parseInt(e.target.value, 10)); setPage(0); }}
              rowsPerPageOptions={[10, 25, 50, 100]}
              labelRowsPerPage="Lignes par page"
            />
          </TableContainer>
        </>
      )}

      {/* Onglet mouvements */}
      {tab === 2 && (
        <>
          {mouvError && <Alert severity="error">Erreur de chargement des mouvements</Alert>}
          <TableContainer component={Paper}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Date</TableCell>
                  <TableCell>Dépôt</TableCell>
                  <TableCell>Produit</TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell align="right">Quantité</TableCell>
                  <TableCell align="right">Qte après</TableCell>
                  <TableCell>Motif</TableCell>
                  <TableCell>Utilisateur</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {mouvLoading && (
                  <TableRow>
                    <TableCell colSpan={8} align="center">Chargement...</TableCell>
                  </TableRow>
                )}
                {mouvementData?.content.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={8} align="center">Aucun mouvement</TableCell>
                  </TableRow>
                )}
                {mouvementData?.content.map((m) => (
                  <TableRow key={m.id} hover>
                    <TableCell>{new Date(m.dateMouvement).toLocaleDateString('fr-FR')} {new Date(m.dateMouvement).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}</TableCell>
                    <TableCell>Depot {m.depotCode}</TableCell>
                    <TableCell>{m.produitDesignation}</TableCell>
                    <TableCell><Chip label={libelle(TYPE_MOUVEMENT, m.type)} size="small" color={MOUVEMENT_COLORS[m.type]} /></TableCell>
                    {/* La quantite est deja signee (+entree / -sortie / delta d'ajustement) :
                        on affiche son vrai signe, sinon un ajustement de -300 se lit "300". */}
                    <TableCell align="right" style={{ color: m.quantite > 0 ? 'green' : m.quantite < 0 ? 'red' : 'inherit' }}>
                      {m.quantite > 0 ? `+${m.quantite}` : m.quantite}
                    </TableCell>
                    <TableCell align="right">{m.quantiteApres}</TableCell>
                    <TableCell>{m.motif ?? '-'}</TableCell>
                    <TableCell>{m.utilisateur ?? '-'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={mouvementData?.totalElements ?? 0}
              page={page}
              onPageChange={(_, newPage) => setPage(newPage)}
              rowsPerPage={size}
              onRowsPerPageChange={(e) => { setSize(parseInt(e.target.value, 10)); setPage(0); }}
              rowsPerPageOptions={[10, 25, 50, 100]}
              labelRowsPerPage="Lignes par page"
            />
          </TableContainer>
        </>
      )}

      {/* Dialog Entree */}
      <Dialog open={entreeOpen} onClose={() => { setEntreeOpen(false); resetForms(); }} fullWidth maxWidth="sm">
        <DialogTitle>Entrée en stock</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Produit" select value={produitId} onChange={(e) => setProduitId(Number(e.target.value))} required>
              {produitsList.map((p) => <MenuItem key={p.id} value={p.id}>{p.designation}</MenuItem>)}
            </TextField>
            <TextField label="Dépôt" select value={depotCode} onChange={(e) => setDepotCode(e.target.value)} required>
              {depotsList.map((d) => <MenuItem key={d.id} value={d.code}>Depot {d.code}</MenuItem>)}
            </TextField>
            <TextField label="Quantité" type="number" value={quantite} onChange={(e) => setQuantite(parseFloat(e.target.value) || 1)} slotProps={{ htmlInput: { step: 'any', min: 0 } }} required />
            <TextField label="Motif" value={motif} onChange={(e) => setMotif(e.target.value)} />
            {formError && <Alert severity="error">{formError}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setEntreeOpen(false); resetForms(); }}>Annuler</Button>
          <Button variant="contained" onClick={() => { setFormError(null); entreeMutation.mutate({ produitId, depotCode, quantite, motif: motif || undefined }); }} disabled={entreeMutation.isPending}>
            Valider
          </Button>
        </DialogActions>
      </Dialog>

      {/* Dialog Sortie */}
      <Dialog open={sortieOpen} onClose={() => { setSortieOpen(false); resetForms(); }} fullWidth maxWidth="sm">
        <DialogTitle>Sortie de stock</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Produit" select value={produitId} onChange={(e) => setProduitId(Number(e.target.value))} required>
              {produitsList.map((p) => <MenuItem key={p.id} value={p.id}>{p.designation}</MenuItem>)}
            </TextField>
            <TextField label="Dépôt" select value={depotCode} onChange={(e) => setDepotCode(e.target.value)} required>
              {depotsList.map((d) => <MenuItem key={d.id} value={d.code}>Depot {d.code}</MenuItem>)}
            </TextField>
            <TextField label="Quantité" type="number" value={quantite} onChange={(e) => setQuantite(parseFloat(e.target.value) || 1)} slotProps={{ htmlInput: { step: 'any', min: 0 } }} required />
            <TextField label="Motif" value={motif} onChange={(e) => setMotif(e.target.value)} />
            {formError && <Alert severity="error">{formError}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setSortieOpen(false); resetForms(); }}>Annuler</Button>
          <Button variant="contained" color="error" onClick={() => { setFormError(null); sortieMutation.mutate({ produitId, depotCode, quantite, motif: motif || undefined }); }} disabled={sortieMutation.isPending}>
            Valider
          </Button>
        </DialogActions>
      </Dialog>

      {/* Dialog Ajustement */}
      <Dialog open={ajustOpen} onClose={() => { setAjustOpen(false); resetForms(); }} fullWidth maxWidth="sm">
        <DialogTitle>Ajustement de stock</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Produit" select value={produitId} onChange={(e) => setProduitId(Number(e.target.value))} required>
              {produitsList.map((p) => <MenuItem key={p.id} value={p.id}>{p.designation}</MenuItem>)}
            </TextField>
            <TextField label="Dépôt" select value={depotCode} onChange={(e) => setDepotCode(e.target.value)} required>
              {depotsList.map((d) => <MenuItem key={d.id} value={d.code}>Depot {d.code}</MenuItem>)}
            </TextField>
            <TextField label="Nouvelle quantité" type="number" value={nouvelleQuantite} onChange={(e) => setNouvelleQuantite(parseFloat(e.target.value) || 0)} slotProps={{ htmlInput: { step: 'any', min: 0 } }} required />
            {formError && <Alert severity="error">{formError}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setAjustOpen(false); resetForms(); }}>Annuler</Button>
          <Button variant="contained" onClick={() => { setFormError(null); ajustMutation.mutate({ produitId, depotCode, nouvelleQuantite }); }} disabled={ajustMutation.isPending}>
            Valider
          </Button>
        </DialogActions>
      </Dialog>

      {/* Dialog Transfert */}
      <Dialog open={transfertOpen} onClose={() => { setTransfertOpen(false); resetForms(); }} fullWidth maxWidth="sm">
        <DialogTitle>Transfert inter-dépôt</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Produit" select value={produitId} onChange={(e) => setProduitId(Number(e.target.value))} required>
              {produitsList.map((p) => <MenuItem key={p.id} value={p.id}>{p.designation}</MenuItem>)}
            </TextField>
            <TextField label="Dépôt source" select value={depotCode} onChange={(e) => setDepotCode(e.target.value)} required>
              {depotsList.map((d) => <MenuItem key={d.id} value={d.code}>Depot {d.code}</MenuItem>)}
            </TextField>
            <TextField label="Dépôt destination" select value={depotDest} onChange={(e) => setDepotDest(e.target.value)} required>
              {depotsList.filter((d) => d.code !== depotCode).map((d) => <MenuItem key={d.id} value={d.code}>Depot {d.code}</MenuItem>)}
            </TextField>
            <TextField label="Quantité" type="number" value={quantite} onChange={(e) => setQuantite(parseFloat(e.target.value) || 1)} slotProps={{ htmlInput: { step: 'any', min: 0 } }} required />
            {formError && <Alert severity="error">{formError}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setTransfertOpen(false); resetForms(); }}>Annuler</Button>
          <Button variant="contained" onClick={() => { setFormError(null); transfertMutation.mutate({ produitId, depotSource: depotCode, depotDestination: depotDest, quantite }); }} disabled={transfertMutation.isPending}>
            Transférer
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
