import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
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
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import { creerDepot, listerDepots, modifierDepot, supprimerDepot } from '../api/depots';
import type { Depot } from '../api/types';

const messageErreur = (e: unknown, defaut: string) =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? defaut;

/** Reseau de depots : structure du stock, du ressort de l'administrateur. */
export default function SectionDepots() {
  const queryClient = useQueryClient();
  const [dialogOpen, setDialogOpen] = useState(false);
  // null = création, sinon modification du dépôt porte
  const [editId, setEditId] = useState<number | null>(null);
  const [code, setCode] = useState('');
  const [formErreur, setFormErreur] = useState<string | null>(null);
  const [aSupprimer, setASupprimer] = useState<Depot | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [succes, setSucces] = useState<string | null>(null);

  const { data: depots, isLoading, isError } = useQuery({
    queryKey: ['depots-list'],
    queryFn: listerDepots,
  });

  const rafraichir = () => queryClient.invalidateQueries({ queryKey: ['depots-list'] });

  const creerMutation = useMutation({
    mutationFn: creerDepot,
    onSuccess: (d) => {
      rafraichir();
      fermer();
      setErreur(null);
      setSucces(`Dépôt ${d.code} cree`);
    },
    onError: (e) => setFormErreur(messageErreur(e, 'Création impossible')),
  });

  const modifierMutation = useMutation({
    mutationFn: ({ id, nouveauCode }: { id: number; nouveauCode: string }) =>
      modifierDepot(id, nouveauCode),
    onSuccess: (d) => {
      rafraichir();
      fermer();
      setErreur(null);
      setSucces(`Dépôt renomme en ${d.code}`);
    },
    onError: (e) => setFormErreur(messageErreur(e, 'Modification impossible')),
  });

  const supprimerMutation = useMutation({
    mutationFn: supprimerDepot,
    onSuccess: () => {
      rafraichir();
      setASupprimer(null);
      setErreur(null);
      setSucces('Dépôt supprime');
    },
    onError: (e) => {
      setASupprimer(null);
      setSucces(null);
      setErreur(messageErreur(e, 'Suppression impossible'));
    },
  });

  const fermer = () => {
    setDialogOpen(false);
    setEditId(null);
    setCode('');
    setFormErreur(null);
  };

  const ouvrir = (depot?: Depot) => {
    setEditId(depot?.id ?? null);
    setCode(depot?.code ?? '');
    setFormErreur(null);
    setDialogOpen(true);
  };

  const soumettre = () => {
    const saisi = code.trim();
    if (!saisi) {
      setFormErreur('Le code est obligatoire');
      return;
    }
    if (editId === null) {
      creerMutation.mutate(saisi);
    } else {
      modifierMutation.mutate({ id: editId, nouveauCode: saisi });
    }
  };

  return (
    <Paper sx={{ p: 3, mt: 3 }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h6">Dépôts</Typography>
        <Button size="small" variant="contained" startIcon={<AddIcon />} onClick={() => ouvrir()}>
          Nouveau depot
        </Button>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1, mb: 2 }}>
        Le code identifie le depot dans toute l'application ; il est enregistre en
        majuscules. Un depot qui a recu du stock ou figure dans des mouvements ne peut
        plus etre supprime, pour que l'historique reste lisible.
      </Typography>

      {isLoading && <Typography>Chargement...</Typography>}
      {isError && <Alert severity="error">Erreur de chargement des dépôts</Alert>}
      {erreur && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setErreur(null)}>{erreur}</Alert>}
      {succes && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSucces(null)}>{succes}</Alert>}

      {depots && (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Code</TableCell>
                <TableCell align="right" sx={{ width: 120 }} />
              </TableRow>
            </TableHead>
            <TableBody>
              {depots.map((d) => (
                <TableRow key={d.id}>
                  <TableCell>{d.code}</TableCell>
                  <TableCell align="right">
                    <Tooltip title="Renommer">
                      <IconButton size="small" onClick={() => ouvrir(d)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Supprimer">
                      <IconButton size="small" color="error" onClick={() => setASupprimer(d)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={dialogOpen} onClose={fermer} fullWidth maxWidth="xs">
        <DialogTitle>{editId === null ? 'Nouveau dépôt' : 'Renommer le dépôt'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              helperText="Ex. SH, AB. Enregistré en majuscules."
              onKeyDown={(e) => { if (e.key === 'Enter') soumettre(); }}
              required
              autoFocus
            />
            {formErreur && <Alert severity="error">{formErreur}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={fermer}>Annuler</Button>
          <Button
            variant="contained"
            onClick={soumettre}
            disabled={creerMutation.isPending || modifierMutation.isPending}
          >
            {editId === null ? 'Créer' : 'Enregistrer'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={aSupprimer !== null} onClose={() => setASupprimer(null)}>
        <DialogTitle>Supprimer ce dépôt ?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Le depot {aSupprimer?.code} sera efface. S'il porte du stock ou figure dans des
            mouvements, la suppression sera refusee.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setASupprimer(null)}>Annuler</Button>
          <Button
            color="error"
            variant="contained"
            disabled={supprimerMutation.isPending}
            onClick={() => aSupprimer && supprimerMutation.mutate(aSupprimer.id)}
          >
            Supprimer
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  );
}
