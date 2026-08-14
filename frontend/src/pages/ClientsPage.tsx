import { useState } from 'react';
import { libelle, ROLE, TYPE_TIERS } from '../utils/libelles';
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
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import VisibilityIcon from '@mui/icons-material/Visibility';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import { creerClient, listerClients, modifierClient, reattribuerClient, supprimerClient, type ClientQuery } from '../api/clients';
import { listerUtilisateurs } from '../api/utilisateurs';
import { useAuth } from '../auth/AuthContext';
import { droits } from '../auth/droits';
import type { Client, ClientRequest, TypeClient } from '../api/types';
import { EditeurRibs, EditeurTelephones } from '../components/TiersChamps';
import {
  iceInvalide,
  identifiantFiscalInvalide,
  MESSAGE_ICE,
  MESSAGE_IDENTIFIANT_FISCAL,
} from '../utils/validation';

const EMPTY_FORM: ClientRequest = { nom: '', email: '', typeClient: 'ENTREPRISE' };

export default function ClientsPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { user } = useAuth();
  // Plafond, blocage et reattribution relevent de l'encadrement commercial
  const mesDroits = droits(user?.role);
  const estAdmin = mesDroits.encadrer;
  // Reattribution : réservée a l'admin (depart d'un commercial, reorganisation)
  const [reattribClient, setReattribClient] = useState<Client | null>(null);
  const [nouveauCommercialId, setNouveauCommercialId] = useState<number | ''>('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [recherche, setRecherche] = useState('');
  const [typeFilter, setTypeFilter] = useState<TypeClient | ''>('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [form, setForm] = useState<ClientRequest>(EMPTY_FORM);
  const [formError, setFormError] = useState<string | null>(null);

  const query: ClientQuery = {
    page,
    size,
    sort: 'nom,asc',
    recherche: recherche || undefined,
    typeClient: typeFilter || undefined,
  };

  const { data, isLoading, isError } = useQuery({
    queryKey: ['clients', query],
    queryFn: () => listerClients(query),
  });

  const createMutation = useMutation({
    mutationFn: creerClient,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['clients'] }); setDialogOpen(false); setForm(EMPTY_FORM); },
    onError: () => setFormError('Création impossible (email déjà utilise ?)'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: ClientRequest }) => modifierClient(id, payload),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['clients'] }); setDialogOpen(false); setEditId(null); setForm(EMPTY_FORM); },
    onError: () => setFormError('Modification impossible'),
  });

  const deleteMutation = useMutation({
    mutationFn: supprimerClient,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['clients'] }),
  });

  // Liste des commerciaux, chargee seulement pour l'admin (seul concerne)
  // Un client se confie a la force de vente : les autrès rôles n'ont pas de
  // portefeuille, les proposer n'aurait pas de sens (et le backend refuse).
  const { data: utilisateurs } = useQuery({
    queryKey: ['utilisateurs-attribuables'],
    queryFn: () => listerUtilisateurs(),
    enabled: estAdmin,
  });

  const reattribMutation = useMutation({
    mutationFn: ({ id, commercialId }: { id: number; commercialId: number }) =>
      reattribuerClient(id, commercialId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clients'] });
      setReattribClient(null);
      setNouveauCommercialId('');
    },
  });

  const openEdit = (c: Client) => {
    setEditId(c.id);
    setForm({
      nom: c.nom, prenom: c.prenom, email: c.email,
      telephones: c.telephones, ribs: c.ribs, adresse: c.adresse,
      typeClient: c.typeClient,
      raisonSociale: c.raisonSociale, ice: c.ice, identifiantFiscal: c.identifiantFiscal,
      contactNom: c.contactNom, contactPrenom: c.contactPrenom,
      dateNaissance: c.dateNaissance, cin: c.cin,
    });
    setDialogOpen(true);
  };

  const openCreate = () => { setEditId(null); setForm(EMPTY_FORM); setDialogOpen(true); };

  const handleSubmit = () => {
    setFormError(null);
    if (!form.nom || !form.email) { setFormError('Nom et email sont obligatoires'); return; }
    // La raison sociale est obligatoire en base pour une entreprise
    if (form.typeClient === 'ENTREPRISE' && !form.raisonSociale?.trim()) {
      setFormError('La raison sociale est obligatoire pour une entreprise');
      return;
    }
    // L'ICE est optionnel, mais doit être valide s'il est saisi
    if (form.typeClient === 'ENTREPRISE' && iceInvalide(form.ice)) {
      setFormError(MESSAGE_ICE);
      return;
    }
    // L'identifiant fiscal est optionnel, mais doit être valide s'il est saisi
    if (form.typeClient === 'ENTREPRISE' && identifiantFiscalInvalide(form.identifiantFiscal)) {
      setFormError(MESSAGE_IDENTIFIANT_FISCAL);
      return;
    }
    if (editId) { updateMutation.mutate({ id: editId, payload: form }); }
    else { createMutation.mutate(form); }
  };

  return (
    <Box>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h4">Clients</Typography>
        {mesDroits.ecrireCommercial && (<Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>Nouveau client</Button>)}
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
            onChange={(e) => { setPage(0); setTypeFilter(e.target.value as TypeClient | ''); }}
            sx={{ width: 200 }}
          >
            <MenuItem value="">Tous</MenuItem>
            <MenuItem value="ENTREPRISE">Entreprise</MenuItem>
            <MenuItem value="PARTICULIER">Particulier</MenuItem>
          </TextField>
        </Stack>
      </Paper>

      {isError && <Alert severity="error">Erreur de chargement des clients</Alert>}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Nom</TableCell>
              <TableCell>Prénom / Contact</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Type</TableCell>
              <TableCell>Commercial</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && <TableRow><TableCell colSpan={6} align="center">Chargement...</TableCell></TableRow>}
            {data?.content.length === 0 && <TableRow><TableCell colSpan={6} align="center">Aucun client</TableCell></TableRow>}
            {data?.content.map((c) => (
              <TableRow key={c.id} hover>
                <TableCell>
                  <strong>{c.nom}</strong>
                  {c.statut === 'BLOQUE' && <Chip label="Bloqué" size="small" color="error" sx={{ ml: 1 }} />}
                  {c.typeClient === 'ENTREPRISE' && c.raisonSociale && <><br /><Typography variant="caption" color="text.secondary">{c.raisonSociale}</Typography></>}
                </TableCell>
                <TableCell>{c.typeClient === 'PARTICULIER' ? (c.prenom ?? '-') : (c.contactNom ? `${c.contactPrenom ?? ''} ${c.contactNom}` : '-')}</TableCell>
                <TableCell>{c.email}</TableCell>
                <TableCell><Chip label={libelle(TYPE_TIERS, c.typeClient)} size="small" color={c.typeClient === 'ENTREPRISE' ? 'primary' : 'default'} /></TableCell>
                <TableCell>{c.commercialNom ?? '-'}</TableCell>
                <TableCell align="right">
                  {estAdmin && (
                    <Tooltip title="Réattribuer à un autre commercial">
                      <IconButton
                        size="small"
                        onClick={() => {
                          setReattribClient(c);
                          setNouveauCommercialId(c.commercialId ?? '');
                        }}
                      >
                        <SwapHorizIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                  <IconButton size="small" onClick={() => navigate(`/clients/${c.id}`)}><VisibilityIcon fontSize="small" /></IconButton>
                  {mesDroits.ecrireCommercial && (<IconButton size="small" onClick={() => openEdit(c)}><EditIcon fontSize="small" /></IconButton>)}
                  {mesDroits.ecrireCommercial && (<IconButton color="error" size="small" onClick={() => deleteMutation.mutate(c.id)}><DeleteIcon fontSize="small" /></IconButton>)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <TablePagination component="div" count={data?.totalElements ?? 0} page={page} onPageChange={(_, p) => setPage(p)} rowsPerPage={size} onRowsPerPageChange={(e) => { setSize(parseInt(e.target.value, 10)); setPage(0); }} rowsPerPageOptions={[10, 25, 50, 100]} labelRowsPerPage="Lignes par page" />
      </TableContainer>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{editId ? 'Modifier le client' : 'Nouveau client'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {/* Le type est fige apres creation (heritage en base) : le back refuse le changement */}
            <TextField label="Type de client" select value={form.typeClient} disabled={!!editId} helperText={editId ? 'Le type ne peut pas être change après création' : undefined} onChange={(e) => setForm({ ...form, typeClient: e.target.value as TypeClient })}>
              <MenuItem value="ENTREPRISE">Entreprise</MenuItem>
              <MenuItem value="PARTICULIER">Particulier</MenuItem>
            </TextField>

            {form.typeClient === 'ENTREPRISE' && (
              <>
                <TextField label="Nom commercial" value={form.nom} onChange={(e) => setForm({ ...form, nom: e.target.value })} required helperText="Nom d'usage, ex : Hotel Mogador" />
                <TextField label="Raison sociale" value={form.raisonSociale ?? ''} onChange={(e) => setForm({ ...form, raisonSociale: e.target.value })} required helperText="Denomination legale, ex : Hotel Mogador Palace SARL" />
                <TextField label="ICE" value={form.ice ?? ''} onChange={(e) => setForm({ ...form, ice: e.target.value })} error={iceInvalide(form.ice)} helperText={iceInvalide(form.ice) ? MESSAGE_ICE : "Identifiant Commun de l'Entreprise (15 chiffres, optionnel)"} />
                <TextField label="Identifiant fiscal" value={form.identifiantFiscal ?? ''} onChange={(e) => setForm({ ...form, identifiantFiscal: e.target.value })} error={identifiantFiscalInvalide(form.identifiantFiscal)} helperText={identifiantFiscalInvalide(form.identifiantFiscal) ? MESSAGE_IDENTIFIANT_FISCAL : '8 chiffres, optionnel'} />
                <TextField label="Nom du contact" value={form.contactNom ?? ''} onChange={(e) => setForm({ ...form, contactNom: e.target.value })} />
                <TextField label="Prénom du contact" value={form.contactPrenom ?? ''} onChange={(e) => setForm({ ...form, contactPrenom: e.target.value })} />
              </>
            )}

            {form.typeClient === 'PARTICULIER' && (
              <>
                <TextField label="Nom" value={form.nom} onChange={(e) => setForm({ ...form, nom: e.target.value })} required />
                <TextField label="Prénom" value={form.prenom ?? ''} onChange={(e) => setForm({ ...form, prenom: e.target.value })} />
                <TextField label="CIN" value={form.cin ?? ''} onChange={(e) => setForm({ ...form, cin: e.target.value })} helperText="Carte d'identité nationale (optionnel)" />
                <TextField label="Date de naissance" type="date" value={form.dateNaissance ?? ''} onChange={(e) => setForm({ ...form, dateNaissance: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
              </>
            )}

            <TextField label="Email" type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
            <EditeurTelephones valeurs={form.telephones} onChange={(telephones) => setForm({ ...form, telephones })} />
            <EditeurRibs valeurs={form.ribs} onChange={(ribs) => setForm({ ...form, ribs })} />
            <TextField label="Adresse" value={form.adresse ?? ''} onChange={(e) => setForm({ ...form, adresse: e.target.value })} />
            {/* Le plafond de credit se definit sur la fiche du client, apres creation */}
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

      {/* Reattribution (admin uniquement) */}
      <Dialog open={!!reattribClient} onClose={() => setReattribClient(null)} fullWidth maxWidth="xs">
        <DialogTitle>Réattribuer le client</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              <strong>{reattribClient?.nom}</strong> est actuellement suivi par{' '}
              {reattribClient?.commercialNom ?? 'personne'}.
            </Typography>
            <TextField
              label="Nouveau commercial"
              select
              value={nouveauCommercialId}
              onChange={(e) => setNouveauCommercialId(Number(e.target.value))}
            >
              {(utilisateurs ?? [])
                .filter((u) => u.role === 'COMMERCIAL' || u.role === 'RESPONSABLE_COMMERCIAL')
                .map((u) => (
                <MenuItem key={u.id} value={u.id}>
                  {u.prenom} {u.nom} ({libelle(ROLE, u.role)})
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReattribClient(null)}>Annuler</Button>
          <Button
            variant="contained"
            disabled={!nouveauCommercialId || reattribMutation.isPending}
            onClick={() =>
              reattribClient &&
              nouveauCommercialId &&
              reattribMutation.mutate({ id: reattribClient.id, commercialId: nouveauCommercialId })
            }
          >
            Reattribuer
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
