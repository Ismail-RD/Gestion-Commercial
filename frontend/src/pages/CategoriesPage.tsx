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
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import {
  creerCategorie,
  listerCategories,
  modifierCategorie,
  supprimerCategorie,
} from '../api/categories';
import type { Categorie, CategorieRequest } from '../api/types';

const EMPTY_FORM: CategorieRequest = { nom: '', description: '' };

export default function CategoriesPage() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const mesDroits = droits(user?.role);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [form, setForm] = useState<CategorieRequest>(EMPTY_FORM);
  const [formError, setFormError] = useState<string | null>(null);

  const { data: categories = [], isLoading, isError } = useQuery({
    queryKey: ['categories'],
    queryFn: listerCategories,
  });

  const fermerEtReinitialiser = () => {
    setDialogOpen(false);
    setEditId(null);
    setForm(EMPTY_FORM);
  };

  const createMutation = useMutation({
    mutationFn: creerCategorie,
    onSuccess: () => {
      // Les produits affichent le nom de catégorie : leur cache doit suivre
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      queryClient.invalidateQueries({ queryKey: ['categories-list'] });
      fermerEtReinitialiser();
    },
    onError: () => setFormError('Création impossible'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: CategorieRequest }) =>
      modifierCategorie(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      queryClient.invalidateQueries({ queryKey: ['categories-list'] });
      queryClient.invalidateQueries({ queryKey: ['produits'] });
      fermerEtReinitialiser();
    },
    onError: () => setFormError('Modification impossible'),
  });

  const [deleteError, setDeleteError] = useState<string | null>(null);
  const deleteMutation = useMutation({
    mutationFn: supprimerCategorie,
    onSuccess: () => {
      setDeleteError(null);
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      queryClient.invalidateQueries({ queryKey: ['categories-list'] });
    },
    // Une catégorie rattâchee a des produits est protegee par la cle etrangere
    onError: () =>
      setDeleteError("Suppression impossible : cette catégorie est utilisee par des produits"),
  });

  const openEdit = (c: Categorie) => {
    setEditId(c.id);
    setForm({ nom: c.nom, description: c.description });
    setDialogOpen(true);
  };

  const openCreate = () => {
    setEditId(null);
    setForm(EMPTY_FORM);
    setDialogOpen(true);
  };

  const handleSubmit = () => {
    setFormError(null);
    if (!form.nom.trim()) {
      setFormError('Le nom est obligatoire');
      return;
    }
    if (editId) {
      updateMutation.mutate({ id: editId, payload: form });
    } else {
      createMutation.mutate(form);
    }
  };

  return (
    <Box>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h4">Catégories</Typography>
        {mesDroits.ecrireCategorie && (
        <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>
          Nouvelle catégorie
        </Button>
        )}
      </Stack>

      {isError && <Alert severity="error">Erreur de chargement des catégories</Alert>}
      {deleteError && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setDeleteError(null)}>
          {deleteError}
        </Alert>
      )}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Nom</TableCell>
              <TableCell>Description</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && (
              <TableRow>
                <TableCell colSpan={3} align="center">Chargement...</TableCell>
              </TableRow>
            )}
            {!isLoading && categories.length === 0 && (
              <TableRow>
                <TableCell colSpan={3} align="center">Aucune catégorie</TableCell>
              </TableRow>
            )}
            {categories.map((c) => (
              <TableRow key={c.id} hover>
                <TableCell><strong>{c.nom}</strong></TableCell>
                <TableCell>{c.description ?? '-'}</TableCell>
                <TableCell align="right">
                  {mesDroits.ecrireCategorie && (
                    <>
                      <IconButton size="small" onClick={() => openEdit(c)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                      <IconButton color="error" size="small" onClick={() => deleteMutation.mutate(c.id)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <Dialog open={dialogOpen} onClose={fermerEtReinitialiser} fullWidth maxWidth="sm">
        <DialogTitle>{editId ? 'Modifier la catégorie' : 'Nouvelle catégorie'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Nom"
              value={form.nom}
              onChange={(e) => setForm({ ...form, nom: e.target.value })}
              required
            />
            <TextField
              label="Description"
              value={form.description ?? ''}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              multiline
              rows={3}
            />
            {formError && <Alert severity="error">{formError}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={fermerEtReinitialiser}>Annuler</Button>
          <Button
            variant="contained"
            onClick={handleSubmit}
            disabled={createMutation.isPending || updateMutation.isPending}
          >
            {editId ? 'Modifier' : 'Créer'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
