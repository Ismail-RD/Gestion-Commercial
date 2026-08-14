import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
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
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import SendIcon from '@mui/icons-material/Send';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import BlockIcon from '@mui/icons-material/Block';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import { useAuth } from '../auth/AuthContext';
import { listerUtilisateurs } from '../api/utilisateurs';
import {
  changerActivation,
  inviterUtilisateur,
  modifierUtilisateur,
  renvoyerInvitation,
  supprimerUtilisateur,
  type InvitationRequest,
} from '../api/invitations';
import type { Role, Utilisateur } from '../api/types';

const ROLES: { valeur: Role; libelle: string }[] = [
  { valeur: 'ADMIN', libelle: 'Administrateur' },
  { valeur: 'RESPONSABLE_COMMERCIAL', libelle: 'Responsable commercial' },
  { valeur: 'COMMERCIAL', libelle: 'Commercial' },
  { valeur: 'MAGASINIER', libelle: 'Magasinier' },
  { valeur: 'RESPONSABLE_IMPORT', libelle: 'Responsable import' },
  { valeur: 'COMPTABLE', libelle: 'Comptable' },
];

const LIBELLE = (role: Role) => ROLES.find((r) => r.valeur === role)?.libelle ?? role;

const FORM_VIDE: InvitationRequest = { nom: '', prenom: '', email: '', role: 'COMMERCIAL' };

const messageErreur = (e: unknown, defaut: string) =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? defaut;

export default function UtilisateursPage() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const [dialogOpen, setDialogOpen] = useState(false);
  // null = création, sinon modification du compte porte
  const [editId, setEditId] = useState<number | null>(null);
  const [form, setForm] = useState<InvitationRequest>(FORM_VIDE);
  const [formErreur, setFormErreur] = useState<string | null>(null);
  const [aSupprimer, setASupprimer] = useState<Utilisateur | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [succes, setSucces] = useState<string | null>(null);

  const { data: utilisateurs, isLoading, isError } = useQuery({
    queryKey: ['utilisateurs'],
    queryFn: () => listerUtilisateurs(),
  });

  const rafraichir = () => queryClient.invalidateQueries({ queryKey: ['utilisateurs'] });

  const echec = (defaut: string) => (e: unknown) => {
    setSucces(null);
    setErreur(messageErreur(e, defaut));
  };

  const inviterMutation = useMutation({
    mutationFn: inviterUtilisateur,
    onSuccess: (u) => {
      rafraichir();
      fermerDialog();
      setErreur(null);
      setSucces(`Invitation envoyée a ${u.email}`);
    },
    onError: (e) => setFormErreur(messageErreur(e, 'Création impossible')),
  });

  const modifierMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: InvitationRequest }) =>
      modifierUtilisateur(id, payload),
    onSuccess: (u) => {
      rafraichir();
      fermerDialog();
      setErreur(null);
      setSucces(`Compte de ${u.prenom} ${u.nom} mis à jour`);
    },
    onError: (e) => setFormErreur(messageErreur(e, 'Modification impossible')),
  });

  const renvoyerMutation = useMutation({
    mutationFn: renvoyerInvitation,
    onSuccess: (u) => {
      setErreur(null);
      setSucces(`Nouvelle invitation envoyée a ${u.email}`);
    },
    onError: echec('Renvoi impossible'),
  });

  const activationMutation = useMutation({
    mutationFn: ({ id, actif }: { id: number; actif: boolean }) => changerActivation(id, actif),
    onSuccess: (u) => {
      rafraichir();
      setErreur(null);
      setSucces(u.actif
        ? `Accès rendu a ${u.prenom} ${u.nom}`
        : `Accès retire a ${u.prenom} ${u.nom}`);
    },
    onError: echec('Changement impossible'),
  });

  const supprimerMutation = useMutation({
    mutationFn: supprimerUtilisateur,
    onSuccess: () => {
      rafraichir();
      setASupprimer(null);
      setErreur(null);
      setSucces('Compte supprime');
    },
    onError: (e) => {
      setASupprimer(null);
      echec('Suppression impossible')(e);
    },
  });

  const fermerDialog = () => {
    setDialogOpen(false);
    setEditId(null);
    setForm(FORM_VIDE);
    setFormErreur(null);
  };

  const ouvrirCreation = () => {
    setEditId(null);
    setForm(FORM_VIDE);
    setFormErreur(null);
    setDialogOpen(true);
  };

  const ouvrirEdition = (u: Utilisateur) => {
    setEditId(u.id);
    setForm({ nom: u.nom, prenom: u.prenom, email: u.email, role: u.role });
    setFormErreur(null);
    setDialogOpen(true);
  };

  const soumettre = () => {
    if (!form.nom.trim() || !form.prenom.trim() || !form.email.trim()) {
      setFormErreur('Nom, prenom et email sont obligatoires');
      return;
    }
    if (editId === null) {
      inviterMutation.mutate(form);
    } else {
      modifierMutation.mutate({ id: editId, payload: form });
    }
  };

  const enCours = inviterMutation.isPending || modifierMutation.isPending;

  return (
    <Box>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4">Utilisateurs</Typography>
        <Button variant="contained" startIcon={<PersonAddIcon />} onClick={ouvrirCreation}>
          Nouvel utilisateur
        </Button>
      </Stack>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Vous renseignez l'identite et le rôle ; l'interesse recoit un lien par email pour
        choisir lui-meme son mot de passe. Son compte reste inactif jusque-la. Pour un
        depart, desactivez plutot que de supprimer : l'historique reste lisible.
      </Typography>

      {erreur && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setErreur(null)}>{erreur}</Alert>}
      {succes && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSucces(null)}>{succes}</Alert>}
      {isError && <Alert severity="error" sx={{ mb: 2 }}>Erreur de chargement des utilisateurs</Alert>}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Nom</TableCell>
              <TableCell>Prénom</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Rôle</TableCell>
              <TableCell>État</TableCell>
              <TableCell align="right" sx={{ width: 260 }} />
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && (
              <TableRow><TableCell colSpan={6}>Chargement...</TableCell></TableRow>
            )}
            {(utilisateurs ?? []).map((u) => {
              // Se retirer soi-même l'accès refermerait la porte sur soi.
              const moi = u.id === user?.id;
              return (
                <TableRow key={u.id}>
                  <TableCell>{u.nom}</TableCell>
                  <TableCell>{u.prenom}</TableCell>
                  <TableCell>{u.email}</TableCell>
                  <TableCell>{LIBELLE(u.role)}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      label={u.actif ? 'Actif' : 'Invitation en attente'}
                      color={u.actif ? 'success' : 'warning'}
                    />
                  </TableCell>
                  <TableCell align="right">
                    <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                      {/* Un compte actif a deja son mot de passe : plus rien a renvoyer */}
                      {!u.actif && (
                        <Tooltip title="Renvoyer l'invitation">
                          <span>
                            <IconButton
                              size="small"
                              disabled={renvoyerMutation.isPending}
                              onClick={() => renvoyerMutation.mutate(u.id)}
                            >
                              <SendIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                      )}
                      <Tooltip title="Modifier">
                        <IconButton size="small" onClick={() => ouvrirEdition(u)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      {!moi && (
                        <Tooltip title={u.actif ? "Retirer l'accès" : "Rendre l'accès"}>
                          <span>
                            <IconButton
                              size="small"
                              color={u.actif ? 'warning' : 'success'}
                              disabled={activationMutation.isPending}
                              onClick={() => activationMutation.mutate({ id: u.id, actif: !u.actif })}
                            >
                              {u.actif ? <BlockIcon fontSize="small" /> : <CheckCircleIcon fontSize="small" />}
                            </IconButton>
                          </span>
                        </Tooltip>
                      )}
                      {!moi && (
                        <Tooltip title="Supprimer">
                          <IconButton size="small" color="error" onClick={() => setASupprimer(u)}>
                            <DeleteIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                    </Stack>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Creation ou modification : meme formulaire, le mot de passe n'y figure jamais */}
      <Dialog open={dialogOpen} onClose={fermerDialog} fullWidth maxWidth="sm">
        <DialogTitle>{editId === null ? 'Nouvel utilisateur' : 'Modifier le compte'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Nom"
              value={form.nom}
              onChange={(e) => setForm({ ...form, nom: e.target.value })}
              required
            />
            <TextField
              label="Prénom"
              value={form.prenom}
              onChange={(e) => setForm({ ...form, prenom: e.target.value })}
              required
            />
            <TextField
              label="Email"
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              helperText={editId === null
                ? "C'est a cette adresse que part le lien d'inscription"
                : "Sert d'identifiant de connexion"}
              required
            />
            <TextField
              label="Rôle"
              select
              value={form.role}
              onChange={(e) => setForm({ ...form, role: e.target.value as Role })}
              required
            >
              {ROLES.map((r) => (
                <MenuItem key={r.valeur} value={r.valeur}>{r.libelle}</MenuItem>
              ))}
            </TextField>
            {formErreur && <Alert severity="error">{formErreur}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={fermerDialog}>Annuler</Button>
          <Button
            variant="contained"
            startIcon={editId === null ? <SendIcon /> : <EditIcon />}
            onClick={soumettre}
            disabled={enCours}
          >
            {editId === null ? 'Créer et inviter' : 'Enregistrer'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={aSupprimer !== null} onClose={() => setASupprimer(null)}>
        <DialogTitle>Supprimer ce compte ?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Le compte de {aSupprimer?.prenom} {aSupprimer?.nom} sera efface definitivement.
            S'il figure sur des documents, la suppression sera refusee : desactivez-le.
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
    </Box>
  );
}
