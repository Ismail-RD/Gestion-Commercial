import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { droits } from '../auth/droits';
import { useNavigate } from 'react-router-dom';
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
  FormControlLabel,
  IconButton,
  MenuItem,
  Paper,
  Radio,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import IconeRubrique from '@mui/icons-material/Inventory2';
import EnTetePage from '../components/EnTetePage';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import VisibilityIcon from '@mui/icons-material/Visibility';
import {
  creerProduit,
  listerProduits,
  modifierProduit,
  supprimerProduit,
  type ProduitQuery,
} from '../api/produits';
import { listerCategories } from '../api/categories';
import { listerToutesMarques } from '../api/marques';
import { listerFournisseurs } from '../api/fournisseurs';
import type { Produit, ProduitFournisseurRequest, ProduitRequest } from '../api/types';
import { formatMontant } from '../utils/format';

/**
 * L'API accepte marqueIds/fournisseurs absents, mais le formulaire garde
 * toujours des tableaux (plus simple a manipuler). Reste assignable a
 * ProduitRequest a l'envoi.
 */
type ProduitForm = ProduitRequest & {
  marqueIds: number[];
  fournisseurs: ProduitFournisseurRequest[];
};

const EMPTY_FORM: ProduitForm = {
  reference: '',
  designation: '',
  prixUnitaireHT: 0,
  tauxTVA: 20,
  marqueIds: [],
  fournisseurs: [],
};

export default function ProduitsPage() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const mesDroits = droits(user?.role);
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [recherche, setRecherche] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [form, setForm] = useState<ProduitForm>(EMPTY_FORM);
  const [formError, setFormError] = useState<string | null>(null);

  const query: ProduitQuery = {
    page,
    size,
    sort: 'reference,asc',
    recherche: recherche || undefined,
  };

  const { data, isLoading, isError } = useQuery({
    queryKey: ['produits', query],
    queryFn: () => listerProduits(query),
  });

  const { data: categories = [] } = useQuery({
    queryKey: ['categories-list'],
    queryFn: listerCategories,
  });

  const { data: marques = [] } = useQuery({
    queryKey: ['marques-list'],
    queryFn: listerToutesMarques,
  });

  const { data: fournisseursData } = useQuery({
    queryKey: ['fournisseurs-list'],
    queryFn: () => listerFournisseurs({ size: 200 }),
  });
  const fournisseurs = fournisseursData?.content ?? [];

  const createMutation = useMutation({
    mutationFn: creerProduit,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['produits'] }); setDialogOpen(false); setForm(EMPTY_FORM); },
    onError: () => setFormError('Création impossible (référence déjà utilisee ?)'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: ProduitRequest }) => modifierProduit(id, payload),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['produits'] }); setDialogOpen(false); setEditId(null); setForm(EMPTY_FORM); },
    onError: () => setFormError('Modification impossible'),
  });

  const deleteMutation = useMutation({
    mutationFn: supprimerProduit,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['produits'] }),
  });

  const handleCreate = () => {
    setFormError(null);
    if (editId) { updateMutation.mutate({ id: editId, payload: form }); }
    else { createMutation.mutate(form); }
  };

  const openEdit = (p: Produit) => {
    setEditId(p.id);
    setForm({
      reference: p.reference,
      designation: p.designation,
      prixUnitaireHT: p.prixUnitaireHT,
      tauxTVA: p.tauxTVA,
      description: p.description,
      uniteMesure: p.uniteMesure,
      categorieId: p.categorieId,
      marqueIds: p.marques.map((m) => m.id),
      fournisseurs: p.fournisseurs.map((f) => ({
        fournisseurId: f.fournisseurId,
        referenceFournisseur: f.referenceFournisseur,
        estPrincipal: f.estPrincipal,
      })),
    });
    setDialogOpen(true);
  };

  // Reconstruit la liste des liaisons en conservant les attributs déjà saisis
  const majFournisseursSelectionnes = (ids: number[]) => {
    const liens = ids.map(
      (id) =>
        form.fournisseurs.find((f) => f.fournisseurId === id) ?? {
          fournisseurId: id,
          referenceFournisseur: '',
          estPrincipal: false,
        },
    );
    // Un seul fournisseur principal : on garde le premier marque comme tel
    if (liens.length > 0 && !liens.some((l) => l.estPrincipal)) {
      liens[0].estPrincipal = true;
    }
    setForm({ ...form, fournisseurs: liens });
  };

  const majLien = (id: number, champs: Partial<ProduitFournisseurRequest>) => {
    setForm({
      ...form,
      fournisseurs: form.fournisseurs.map((f) =>
        f.fournisseurId === id ? { ...f, ...champs } : f,
      ),
    });
  };

  const definirPrincipal = (id: number) => {
    setForm({
      ...form,
      fournisseurs: form.fournisseurs.map((f) => ({
        ...f,
        estPrincipal: f.fournisseurId === id,
      })),
    });
  };

  const openCreate = () => { setEditId(null); setForm(EMPTY_FORM); setDialogOpen(true); };

  return (
    <Box>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={{ xs: 1.5, sm: 0 }}
        sx={{ justifyContent: 'space-between', alignItems: { xs: 'stretch', sm: 'center' }, mb: 2 }}
      >
        <EnTetePage titre="Produits" icone={<IconeRubrique />} />
        {mesDroits.ecrireCatalogue && (<Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>Nouveau produit</Button>)}
      </Stack>

      <Paper sx={{ p: 2, mb: 2 }}>
        <TextField
          label="Rechercher"
          placeholder="Référence, désignation, description, catégorie…"
          size="small"
          fullWidth
          value={recherche}
          onChange={(e) => { setPage(0); setRecherche(e.target.value); }}
        />
      </Paper>

      {isError && <Alert severity="error">Erreur de chargement des produits</Alert>}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Référence</TableCell>
              <TableCell>Désignation</TableCell>
              <TableCell align="right">Prix HT</TableCell>
              <TableCell align="right">TVA %</TableCell>
              <TableCell>Catégorie</TableCell>
              <TableCell>Marques</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && <TableRow><TableCell colSpan={7} align="center">Chargement...</TableCell></TableRow>}
            {data?.content.length === 0 && <TableRow><TableCell colSpan={7} align="center">Aucun produit</TableCell></TableRow>}
            {data?.content.map((p) => (
              <TableRow key={p.id} hover>
                <TableCell>{p.reference}</TableCell>
                <TableCell>{p.designation}</TableCell>
                <TableCell align="right">{formatMontant(p.prixUnitaireHT)}</TableCell>
                <TableCell align="right">{p.tauxTVA}</TableCell>
                <TableCell>{p.categorieNom ?? '-'}</TableCell>
                <TableCell>{p.marques?.map((m) => <Chip key={m.id} label={m.nom} size="small" sx={{ mr: 0.5 }} />)}</TableCell>
                <TableCell align="right">
                  <IconButton size="small" onClick={() => navigate(`/produits/${p.id}`)}><VisibilityIcon fontSize="small" /></IconButton>
                  {mesDroits.ecrireCatalogue && (<IconButton size="small" onClick={() => openEdit(p)}><EditIcon fontSize="small" /></IconButton>)}
                  {mesDroits.ecrireCatalogue && (<IconButton color="error" size="small" onClick={() => deleteMutation.mutate(p.id)}><DeleteIcon fontSize="small" /></IconButton>)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <TablePagination component="div" count={data?.totalElements ?? 0} page={page} onPageChange={(_, p) => setPage(p)} rowsPerPage={size} onRowsPerPageChange={(e) => { setSize(parseInt(e.target.value, 10)); setPage(0); }} rowsPerPageOptions={[10, 25, 50, 100]} labelRowsPerPage="Lignes par page" />
      </TableContainer>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>{editId ? 'Modifier le produit' : 'Nouveau produit'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Référence" value={form.reference} onChange={(e) => setForm({ ...form, reference: e.target.value })} required />
            <TextField label="Désignation" value={form.designation} onChange={(e) => setForm({ ...form, designation: e.target.value })} required />
            <TextField label="Prix unitaire HT" type="number" value={form.prixUnitaireHT} onChange={(e) => setForm({ ...form, prixUnitaireHT: parseFloat(e.target.value) })} required />
            <TextField label="Taux TVA (%)" type="number" value={form.tauxTVA} onChange={(e) => setForm({ ...form, tauxTVA: parseFloat(e.target.value) })} required />
            <TextField label="Unité de mesure" value={form.uniteMesure ?? ''} onChange={(e) => setForm({ ...form, uniteMesure: e.target.value })} />

            <TextField
              label="Catégorie"
              select
              value={form.categorieId ?? ''}
              onChange={(e) =>
                setForm({ ...form, categorieId: e.target.value ? Number(e.target.value) : undefined })
              }
            >
              <MenuItem value="">Aucune</MenuItem>
              {categories.map((c) => (
                <MenuItem key={c.id} value={c.id}>{c.nom}</MenuItem>
              ))}
            </TextField>

            <TextField
              label="Marques"
              select
              slotProps={{ select: { multiple: true } }}
              value={form.marqueIds}
              onChange={(e) => setForm({ ...form, marqueIds: e.target.value as unknown as number[] })}
            >
              {marques.map((m) => (
                <MenuItem key={m.id} value={m.id}>{m.nom}</MenuItem>
              ))}
            </TextField>

            <TextField
              label="Fournisseurs"
              select
              slotProps={{ select: { multiple: true } }}
              value={form.fournisseurs.map((f) => f.fournisseurId)}
              onChange={(e) =>
                majFournisseursSelectionnes(e.target.value as unknown as number[])
              }
            >
              {fournisseurs.map((f) => (
                <MenuItem key={f.id} value={f.id}>{f.nom}</MenuItem>
              ))}
            </TextField>

            {/* Attributs de chaque liaison : reference chez le fournisseur + fournisseur principal */}
            {form.fournisseurs.map((lien) => {
              const nom = fournisseurs.find((f) => f.id === lien.fournisseurId)?.nom ?? '';
              return (
                <Paper key={lien.fournisseurId} variant="outlined" sx={{ p: 1.5 }}>
                  <Typography variant="caption" color="text.secondary">{nom}</Typography>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mt: 1 }}>
                    <TextField
                      label="Référence chez ce fournisseur"
                      size="small"
                      fullWidth
                      value={lien.referenceFournisseur ?? ''}
                      onChange={(e) =>
                        majLien(lien.fournisseurId, { referenceFournisseur: e.target.value })
                      }
                    />
                    <FormControlLabel
                      control={
                        <Radio
                          checked={!!lien.estPrincipal}
                          onChange={() => definirPrincipal(lien.fournisseurId)}
                        />
                      }
                      label="Principal"
                    />
                  </Stack>
                </Paper>
              );
            })}

            {formError && <Alert severity="error">{formError}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Annuler</Button>
          <Button variant="contained" onClick={handleCreate} disabled={createMutation.isPending || updateMutation.isPending}>
            {editId ? 'Modifier' : 'Créer'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
