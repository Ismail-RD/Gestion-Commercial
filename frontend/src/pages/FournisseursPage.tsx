import { useState } from 'react';
import { libelle, TYPE_TIERS } from '../utils/libelles';
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
  IconButton,
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
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import VisibilityIcon from '@mui/icons-material/Visibility';
import { creerFournisseur, listerFournisseurs, modifierFournisseur, supprimerFournisseur, type FournisseurQuery } from '../api/fournisseurs';
import type { Fournisseur, FournisseurRequest, TypeFournisseur } from '../api/types';
import { EditeurRibs, EditeurTelephones } from '../components/TiersChamps';
import {
  iceInvalide,
  identifiantFiscalInvalide,
  MESSAGE_ICE,
  MESSAGE_IDENTIFIANT_FISCAL,
} from '../utils/validation';

const EMPTY_FORM: FournisseurRequest = { nom: '', typeFournisseur: 'ENTREPRISE' };

export default function FournisseursPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [recherche, setRecherche] = useState('');
  const [typeFilter, setTypeFilter] = useState<TypeFournisseur | ''>('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [form, setForm] = useState<FournisseurRequest>(EMPTY_FORM);
  const [formError, setFormError] = useState<string | null>(null);

  const query: FournisseurQuery = {
    page,
    size,
    sort: 'nom,asc',
    recherche: recherche || undefined,
    typeFournisseur: typeFilter || undefined,
  };

  const { data, isLoading, isError } = useQuery({
    queryKey: ['fournisseurs', query],
    queryFn: () => listerFournisseurs(query),
  });

  const createMutation = useMutation({
    mutationFn: creerFournisseur,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['fournisseurs'] }); setDialogOpen(false); setForm(EMPTY_FORM); },
    onError: () => setFormError('Création impossible (email déjà utilise ?)'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: FournisseurRequest }) => modifierFournisseur(id, payload),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['fournisseurs'] }); setDialogOpen(false); setEditId(null); setForm(EMPTY_FORM); },
    onError: () => setFormError('Modification impossible'),
  });

  const deleteMutation = useMutation({
    mutationFn: supprimerFournisseur,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['fournisseurs'] }),
  });

  const openEdit = (f: Fournisseur) => {
    setEditId(f.id);
    setForm({
      nom: f.nom, email: f.email, telephones: f.telephones, ribs: f.ribs, adresse: f.adresse,
      typeFournisseur: f.typeFournisseur,
      raisonSociale: f.raisonSociale, ice: f.ice, identifiantFiscal: f.identifiantFiscal,
      prenom: f.prenom, cin: f.cin,
    });
    setDialogOpen(true);
  };

  const handleSubmit = () => {
    setFormError(null);
    if (!form.nom) { setFormError('Le nom est obligatoire'); return; }
    // L'ICE est optionnel, mais doit être valide s'il est saisi
    if (form.typeFournisseur === 'ENTREPRISE' && iceInvalide(form.ice)) { setFormError(MESSAGE_ICE); return; }
    // L'identifiant fiscal est optionnel, mais doit être valide s'il est saisi
    if (form.typeFournisseur === 'ENTREPRISE' && identifiantFiscalInvalide(form.identifiantFiscal)) { setFormError(MESSAGE_IDENTIFIANT_FISCAL); return; }
    if (editId) { updateMutation.mutate({ id: editId, payload: form }); }
    else { createMutation.mutate(form); }
  };

  return (
    <Box>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h4">Fournisseurs</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setEditId(null); setForm(EMPTY_FORM); setDialogOpen(true); }}>
          Nouveau fournisseur
        </Button>
      </Stack>

      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', rowGap: 2 }}>
          <TextField
            label="Rechercher"
            placeholder="Nom, prénom, email, adresse, raison sociale, ICE, ident. fiscal, CIN…"
            size="small"
            value={recherche}
            onChange={(e) => { setPage(0); setRecherche(e.target.value); }}
            sx={{ flex: 1, minWidth: 320 }}
          />
          <TextField
            label="Type"
            select
            size="small"
            value={typeFilter}
            onChange={(e) => { setPage(0); setTypeFilter(e.target.value as TypeFournisseur | ''); }}
            sx={{ width: 200 }}
          >
            <MenuItem value="">Tous</MenuItem>
            <MenuItem value="ENTREPRISE">Entreprise</MenuItem>
            <MenuItem value="PARTICULIER">Particulier</MenuItem>
          </TextField>
        </Stack>
      </Paper>

      {isError && <Alert severity="error">Erreur de chargement des fournisseurs</Alert>}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Nom</TableCell>
              <TableCell>Type</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Téléphone</TableCell>
              <TableCell>Adresse</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && <TableRow><TableCell colSpan={6} align="center">Chargement...</TableCell></TableRow>}
            {data?.content.length === 0 && <TableRow><TableCell colSpan={6} align="center">Aucun fournisseur</TableCell></TableRow>}
            {data?.content.map((f) => (
              <TableRow key={f.id} hover>
                <TableCell><strong>{f.nom}</strong></TableCell>
                <TableCell><Chip label={libelle(TYPE_TIERS, f.typeFournisseur)} size="small" /></TableCell>
                <TableCell>{f.email ?? '-'}</TableCell>
                <TableCell>{f.telephones?.length ? f.telephones.join(', ') : '-'}</TableCell>
                <TableCell>{f.adresse ?? '-'}</TableCell>
                <TableCell align="right">
                  <IconButton size="small" onClick={() => navigate(`/fournisseurs/${f.id}`)}><VisibilityIcon fontSize="small" /></IconButton>
                  <IconButton size="small" onClick={() => openEdit(f)}><EditIcon fontSize="small" /></IconButton>
                  <IconButton color="error" size="small" onClick={() => deleteMutation.mutate(f.id)}><DeleteIcon fontSize="small" /></IconButton>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <TablePagination component="div" count={data?.totalElements ?? 0} page={page} onPageChange={(_, p) => setPage(p)} rowsPerPage={size} onRowsPerPageChange={(e) => { setSize(parseInt(e.target.value, 10)); setPage(0); }} rowsPerPageOptions={[10, 25, 50, 100]} labelRowsPerPage="Lignes par page" />
      </TableContainer>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{editId ? 'Modifier le fournisseur' : 'Nouveau fournisseur'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Nom" value={form.nom} onChange={(e) => setForm({ ...form, nom: e.target.value })} required />
            <TextField label="Type" select value={form.typeFournisseur} onChange={(e) => setForm({ ...form, typeFournisseur: e.target.value as TypeFournisseur })}>
              <MenuItem value="ENTREPRISE">Entreprise</MenuItem>
              <MenuItem value="PARTICULIER">Particulier</MenuItem>
            </TextField>
            <TextField label="Email" value={form.email ?? ''} onChange={(e) => setForm({ ...form, email: e.target.value })} />
            <EditeurTelephones valeurs={form.telephones} onChange={(telephones) => setForm({ ...form, telephones })} />
            <EditeurRibs valeurs={form.ribs} onChange={(ribs) => setForm({ ...form, ribs })} />
            <TextField label="Adresse" value={form.adresse ?? ''} onChange={(e) => setForm({ ...form, adresse: e.target.value })} />
            {form.typeFournisseur === 'ENTREPRISE' && (
              <>
                <TextField label="Raison sociale" value={form.raisonSociale ?? ''} onChange={(e) => setForm({ ...form, raisonSociale: e.target.value })} />
                <TextField label="ICE" value={form.ice ?? ''} onChange={(e) => setForm({ ...form, ice: e.target.value })} error={iceInvalide(form.ice)} helperText={iceInvalide(form.ice) ? MESSAGE_ICE : "Identifiant Commun de l'Entreprise (15 chiffres, optionnel)"} />
                <TextField label="Identifiant fiscal" value={form.identifiantFiscal ?? ''} onChange={(e) => setForm({ ...form, identifiantFiscal: e.target.value })} error={identifiantFiscalInvalide(form.identifiantFiscal)} helperText={identifiantFiscalInvalide(form.identifiantFiscal) ? MESSAGE_IDENTIFIANT_FISCAL : '8 chiffres, optionnel'} />
              </>
            )}
            {form.typeFournisseur === 'PARTICULIER' && (
              <>
                <TextField label="Prénom" value={form.prenom ?? ''} onChange={(e) => setForm({ ...form, prenom: e.target.value })} />
                <TextField label="CIN" value={form.cin ?? ''} onChange={(e) => setForm({ ...form, cin: e.target.value })} helperText="Carte d'identité nationale (optionnel)" />
              </>
            )}
            {formError && <Alert severity="error">{formError}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Annuler</Button>
          <Button variant="contained" onClick={handleSubmit} disabled={createMutation.isPending || updateMutation.isPending}>
            {editId ? 'Modifier' : 'Créer'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
