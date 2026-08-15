import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { droits } from '../auth/droits';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
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
import { creerMarque, listerMarques, modifierMarque, supprimerMarque, type MarqueQuery } from '../api/marques';
import type { MarqueRequest } from '../api/types';

const EMPTY_FORM: MarqueRequest = { nom: '' };

export default function MarquesPage() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const mesDroits = droits(user?.role);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [nom, setNom] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [form, setForm] = useState<MarqueRequest>(EMPTY_FORM);
  const [formError, setFormError] = useState<string | null>(null);

  const query: MarqueQuery = { page, size, sort: 'nom,asc', nom: nom || undefined };

  const { data, isLoading, isError } = useQuery({
    queryKey: ['marques', query],
    queryFn: () => listerMarques(query),
  });

  const createMutation = useMutation({
    mutationFn: creerMarque,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['marques'] }); setDialogOpen(false); setForm(EMPTY_FORM); },
    onError: () => setFormError('Création impossible (nom déjà utilise ?)'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: MarqueRequest }) => modifierMarque(id, payload),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['marques'] }); setDialogOpen(false); setEditId(null); setForm(EMPTY_FORM); },
    onError: () => setFormError('Modification impossible'),
  });

  const deleteMutation = useMutation({
    mutationFn: supprimerMarque,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['marques'] }),
  });

  const openEdit = (m: { id: number; nom: string; logo?: string; telephone?: string; email?: string; adresse?: string; siteWeb?: string }) => {
    setEditId(m.id);
    setForm({ nom: m.nom, logo: m.logo, telephone: m.telephone, email: m.email, adresse: m.adresse, siteWeb: m.siteWeb });
    setDialogOpen(true);
  };

  const handleSubmit = () => {
    setFormError(null);
    if (!form.nom) { setFormError('Le nom est obligatoire'); return; }
    if (editId) { updateMutation.mutate({ id: editId, payload: form }); }
    else { createMutation.mutate(form); }
  };

  return (
    <Box>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={{ xs: 1.5, sm: 0 }}
        sx={{ justifyContent: 'space-between', alignItems: { xs: 'stretch', sm: 'center' }, mb: 2 }}
      >
        <Typography variant="h4">Marques</Typography>
        {mesDroits.ecrireCatalogue && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setEditId(null); setForm(EMPTY_FORM); setDialogOpen(true); }}>
            Nouvelle marque
          </Button>
        )}
      </Stack>

      <Paper sx={{ p: 2, mb: 2 }}>
        <TextField label="Rechercher par nom" size="small" value={nom} onChange={(e) => { setPage(0); setNom(e.target.value); }} sx={{ width: 320 }} />
      </Paper>

      {isError && <Alert severity="error">Erreur de chargement des marques</Alert>}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Nom</TableCell>
              <TableCell>Téléphone</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Site web</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && <TableRow><TableCell colSpan={5} align="center">Chargement...</TableCell></TableRow>}
            {data?.content.length === 0 && <TableRow><TableCell colSpan={5} align="center">Aucune marque</TableCell></TableRow>}
            {data?.content.map((m) => (
              <TableRow key={m.id} hover>
                <TableCell><strong>{m.nom}</strong></TableCell>
                <TableCell>{m.telephone ?? '-'}</TableCell>
                <TableCell>{m.email ?? '-'}</TableCell>
                <TableCell>{m.siteWeb ?? '-'}</TableCell>
                <TableCell align="right">
                  {mesDroits.ecrireCatalogue && (<IconButton size="small" onClick={() => openEdit(m)}><EditIcon fontSize="small" /></IconButton>)}
                  {mesDroits.ecrireCatalogue && (<IconButton color="error" size="small" onClick={() => deleteMutation.mutate(m.id)}><DeleteIcon fontSize="small" /></IconButton>)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <TablePagination component="div" count={data?.totalElements ?? 0} page={page} onPageChange={(_, p) => setPage(p)} rowsPerPage={size} onRowsPerPageChange={(e) => { setSize(parseInt(e.target.value, 10)); setPage(0); }} rowsPerPageOptions={[10, 25, 50, 100]} labelRowsPerPage="Lignes par page" />
      </TableContainer>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{editId ? 'Modifier la marque' : 'Nouvelle marque'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="Nom" value={form.nom} onChange={(e) => setForm({ ...form, nom: e.target.value })} required />
            <TextField label="Logo (URL)" value={form.logo ?? ''} onChange={(e) => setForm({ ...form, logo: e.target.value })} />
            <TextField label="Téléphone" value={form.telephone ?? ''} onChange={(e) => setForm({ ...form, telephone: e.target.value })} />
            <TextField label="Email" value={form.email ?? ''} onChange={(e) => setForm({ ...form, email: e.target.value })} />
            <TextField label="Adresse" value={form.adresse ?? ''} onChange={(e) => setForm({ ...form, adresse: e.target.value })} />
            <TextField label="Site web" value={form.siteWeb ?? ''} onChange={(e) => setForm({ ...form, siteWeb: e.target.value })} />
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
