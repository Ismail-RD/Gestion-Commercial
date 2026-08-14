import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Autocomplete,
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
  Snackbar,
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
import SendIcon from '@mui/icons-material/Send';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import GavelIcon from '@mui/icons-material/Gavel';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import EmailIcon from '@mui/icons-material/Email';
import DownloadIcon from '@mui/icons-material/Download';
import EditIcon from '@mui/icons-material/Edit';
import {
  accepterDevis,
  creerDevis,
  envoyerDevis,
  envoyerDevisParEmail,
  getDevis,
  listerDevis,
  modifierDevis,
  refuserDevis,
  refuserRemiseDevis,
  supprimerDevis,
  telechargerBonCommande,
  telechargerDevisPdf,
  validerRemiseDevis,
  type DevisQuery,
} from '../api/devis';
import { listerClients } from '../api/clients';
import { listerProduits } from '../api/produits';
import type { Devis, DevisRequest, LigneDevisRequest, StatutDevis } from '../api/types';
import { formatMontant } from '../utils/format';
import SuiviDates from '../components/SuiviDates';
import { creerCommandeDepuisDevis, listerCommandes } from '../api/commandes';
import { useAuth } from '../auth/AuthContext';
import { droits } from '../auth/droits';

/** Extrait le message d'erreur renvoye par le backend (sinon un texte par defaut). */
function messageErreur(err: unknown, defaut: string): string {
  const data = (err as { response?: { data?: { message?: string } } })?.response?.data;
  return data?.message ?? defaut;
}

const STATUT_COLORS: Record<StatutDevis, 'default' | 'info' | 'success' | 'error' | 'warning' | 'secondary'> = {
  BROUILLON: 'default',
  EN_ATTENTE_VALIDATION: 'secondary',
  ENVOYE: 'info',
  ACCEPTE: 'success',
  REFUSE: 'error',
  EXPIRE: 'warning',
};

/** Le statut se lit en français, pas en constante technique. */
const STATUT_LABELS: Record<StatutDevis, string> = {
  BROUILLON: 'Brouillon',
  EN_ATTENTE_VALIDATION: 'Remise à valider',
  ENVOYE: 'Envoyé',
  ACCEPTE: 'Accepté',
  REFUSE: 'Refusé',
  EXPIRE: 'Expiré',
};

const EMPTY_LIGNE: LigneDevisRequest = {
  produitId: 0,
  quantite: 1,
  prixUnitaire: 0,
  tauxTVA: 20,
  remise: 0,
};

export default function DevisPage() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const mesDroits = droits(user?.role);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [recherche, setRecherche] = useState('');
  const [statutFilter, setStatutFilter] = useState('');
  const [dateMin, setDateMin] = useState('');
  const [dateMax, setDateMax] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  // Devis en cours de correction ; null = création
  const [editId, setEditId] = useState<number | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedDevis, setSelectedDevis] = useState<number | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  // Retours utilisateur autour de la création de commande
  const [commandeError, setCommandeError] = useState<string | null>(null);
  const [commandeSuccess, setCommandeSuccess] = useState<string | null>(null);
  // Retours utilisateur autour de l'envoi du devis au client par email
  const [emailError, setEmailError] = useState<string | null>(null);
  const [emailSuccess, setEmailSuccess] = useState<string | null>(null);

  // Form création
  const [clientId, setClientId] = useState<number>(0);
  // Client d'origine du devis en cours d'edition : lui seul echappe au refus
  // des clients bloqués, pour qu'un devis existant reste corrigeable.
  const [clientDuDevis, setClientDuDevis] = useState<number | null>(null);
  const [reference, setReference] = useState('');
  const [dateValidite, setDateValidite] = useState('');
  const [lignes, setLignes] = useState<LigneDevisRequest[]>([]);

  const query: DevisQuery = {
    page,
    size,
    sort: 'dateCreation,desc',
    recherche: recherche || undefined,
    statut: (statutFilter as StatutDevis) || undefined,
    dateMin: dateMin || undefined,
    dateMax: dateMax || undefined,
  };

  const { data, isLoading, isError } = useQuery({
    queryKey: ['devis', query],
    queryFn: () => listerDevis(query),
  });

  const { data: clientsData } = useQuery({
    queryKey: ['clients-list'],
    queryFn: () => listerClients({ size: 200 }),
  });

  const { data: produitsData } = useQuery({
    queryKey: ['produits-list'],
    queryFn: () => listerProduits({ size: 1000, sort: 'reference,asc' }),
  });

  const { data: detailDevis } = useQuery({
    queryKey: ['devis-detail', selectedDevis],
    queryFn: () => getDevis(selectedDevis!),
    enabled: selectedDevis !== null,
  });

  // Une commande existe-t-elle déjà pour ce devis ? (evite un bouton "Créer
  // commande" inutile qui se solderait par un 409).
  const { data: commandeExistante } = useQuery({
    queryKey: ['devis-commande', selectedDevis],
    queryFn: () => listerCommandes({ devisId: selectedDevis!, size: 1 }),
    enabled: detailOpen && selectedDevis !== null && detailDevis?.statut === 'ACCEPTE',
  });
  const aDejaCommande = (commandeExistante?.totalElements ?? 0) > 0;

  const createMutation = useMutation({
    mutationFn: creerDevis,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['devis'] });
      setDialogOpen(false);
      resetForm();
    },
    onError: () => setFormError('Erreur lors de la création du devis'),
  });

  // Modification : le même formulaire sert a créer et a corriger un devis.
  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: DevisRequest }) => modifierDevis(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['devis'] });
      queryClient.invalidateQueries({ queryKey: ['devis-detail'] });
      setDialogOpen(false);
      resetForm();
    },
    onError: (err) => setFormError(messageErreur(err, 'Modification du devis impossible')),
  });

  const deleteMutation = useMutation({
    mutationFn: supprimerDevis,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['devis'] }),
  });

  const envoyerMutation = useMutation({
    mutationFn: envoyerDevis,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['devis'] });
      queryClient.invalidateQueries({ queryKey: ['devis-detail'] });
    },
  });

  const accepterMutation = useMutation({
    mutationFn: ({ id }: { id: number }) => accepterDevis(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['devis'] });
      queryClient.invalidateQueries({ queryKey: ['devis-detail'] });
    },
  });

  const refuserMutation = useMutation({
    mutationFn: ({ id }: { id: number }) => refuserDevis(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['devis'] });
      queryClient.invalidateQueries({ queryKey: ['devis-detail'] });
    },
  });

  const validerRemiseMutation = useMutation({
    mutationFn: (id: number) => validerRemiseDevis(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['devis'] });
      queryClient.invalidateQueries({ queryKey: ['devis-detail'] });
    },
  });

  const refuserRemiseMutation = useMutation({
    mutationFn: (id: number) => refuserRemiseDevis(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['devis'] });
      queryClient.invalidateQueries({ queryKey: ['devis-detail'] });
    },
  });

  // Envoi du devis au client par email. N'affecte pas le statut : la reponse du
  // client est une information, l'acceptation reste manuelle ci-dessous.
  const emailMutation = useMutation({
    mutationFn: (id: number) => envoyerDevisParEmail(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['devis'] });
      queryClient.invalidateQueries({ queryKey: ['devis-detail'] });
      setEmailError(null);
      setEmailSuccess('Devis envoyé au client par email');
    },
    onError: (err) => setEmailError(messageErreur(err, "Envoi de l'email impossible")),
  });

  const commandeMutation = useMutation({
    mutationFn: (devisId: number) => creerCommandeDepuisDevis(devisId),
    onSuccess: (commande) => {
      queryClient.invalidateQueries({ queryKey: ['devis'] });
      queryClient.invalidateQueries({ queryKey: ['commandes'] });
      setDetailOpen(false);
      setSelectedDevis(null);
      setCommandeSuccess(`Commande ${commande.numero} créée avec succès`);
    },
    // Ex. client bloque (plafond dépassé) ou stock indisponible : on affiche le
    // message renvoyé par le backend directement dans la fiche du devis.
    onError: (err) => setCommandeError(messageErreur(err, 'Création de la commande impossible')),
  });

  const resetForm = () => {
    setEditId(null);
    setClientId(0);
    setClientDuDevis(null);
    setReference('');
    setDateValidite('');
    setLignes([]);
    setFormError(null);
  };

  const ouvrirCreation = () => { resetForm(); setDialogOpen(true); };

  /** Recharge le formulaire avec le devis a corriger. */
  const ouvrirEdition = (d: Devis) => {
    setEditId(d.id);
    setClientId(d.clientId ?? 0);
    setClientDuDevis(d.clientId ?? null);
    setReference(d.reference ?? '');
    setDateValidite(d.dateValidite);
    setLignes(d.lignes.map((l) => ({
      produitId: l.produitId!,
      quantite: l.quantite,
      prixUnitaire: l.prixUnitaire,
      tauxTVA: l.tauxTVA,
      remise: l.remise ?? 0,
    })));
    setFormError(null);
    setDialogOpen(true);
  };

  const handleSubmit = () => {
    setFormError(null);
    if (!clientId || !dateValidite || lignes.length === 0) {
      setFormError('Veuillez remplir tous les champs obligatoires et ajouter au moins une ligne');
      return;
    }
    const payload = { clientId, reference: reference || undefined, dateValidite, lignes };
    if (editId !== null) {
      updateMutation.mutate({ id: editId, payload });
    } else {
      createMutation.mutate(payload);
    }
  };

  const ouvrirPdf = async (id: number) => {
    const blob = await telechargerDevisPdf(id);
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  };

  /** Ouvre le bon de commande depose par le client, pour verification. */
  const ouvrirBonCommande = async (id: number) => {
    const blob = await telechargerBonCommande(id);
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  };

  const addLigne = () => {
    setLignes([...lignes, { ...EMPTY_LIGNE }]);
  };

  const updateLigne = (index: number, field: keyof LigneDevisRequest, value: string | number) => {
    const updated = [...lignes];
    updated[index] = { ...updated[index], [field]: value };
    if (field === 'produitId') {
      const prod = produitsData?.content.find((p) => p.id === value);
      if (prod) {
        updated[index].prixUnitaire = prod.prixUnitaireHT;
        updated[index].tauxTVA = prod.tauxTVA;
      }
    }
    setLignes(updated);
  };

  const removeLigne = (index: number) => {
    setLignes(lignes.filter((_, i) => i !== index));
  };

  const clients = clientsData?.content ?? [];
  const produits = produitsData?.content ?? [];

  return (
    <Box>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h4">Devis</Typography>
        {mesDroits.ecrireCommercial && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={ouvrirCreation}>
            Nouveau devis
          </Button>
        )}
      </Stack>

      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', rowGap: 2 }}>
          <TextField
            label="Rechercher"
            placeholder="N° devis, référence, client, commercial…"
            size="small"
            value={recherche}
            onChange={(e) => { setPage(0); setRecherche(e.target.value); }}
            sx={{ flex: 1, minWidth: 300 }}
          />
          <TextField
            label="Statut"
            select
            size="small"
            value={statutFilter}
            onChange={(e) => { setPage(0); setStatutFilter(e.target.value); }}
            sx={{ width: 200 }}
          >
            <MenuItem value="">Tous</MenuItem>
            <MenuItem value="BROUILLON">Brouillon</MenuItem>
            <MenuItem value="EN_ATTENTE_VALIDATION">En attente de validation</MenuItem>
            <MenuItem value="ENVOYE">Envoyé</MenuItem>
            <MenuItem value="ACCEPTE">Accepté</MenuItem>
            <MenuItem value="REFUSE">Refusé</MenuItem>
            <MenuItem value="EXPIRE">Expiré</MenuItem>
          </TextField>
          <TextField
            label="Du"
            type="date"
            size="small"
            value={dateMin}
            onChange={(e) => { setPage(0); setDateMin(e.target.value); }}
            slotProps={{ inputLabel: { shrink: true } }}
            sx={{ width: 160 }}
          />
          <TextField
            label="Au"
            type="date"
            size="small"
            value={dateMax}
            onChange={(e) => { setPage(0); setDateMax(e.target.value); }}
            slotProps={{ inputLabel: { shrink: true } }}
            sx={{ width: 160 }}
          />
        </Stack>
      </Paper>

      {isError && <Alert severity="error">Erreur de chargement des devis</Alert>}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Numéro</TableCell>
              <TableCell>Client</TableCell>
              <TableCell>Date création</TableCell>
              <TableCell>Validité</TableCell>
              <TableCell align="right">Montant HT</TableCell>
              <TableCell align="right">Montant TTC</TableCell>
              <TableCell>Statut</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && (
              <TableRow>
                <TableCell colSpan={8} align="center">Chargement...</TableCell>
              </TableRow>
            )}
            {data?.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={8} align="center">Aucun devis</TableCell>
              </TableRow>
            )}
            {data?.content.map((d) => (
              <TableRow key={d.id} hover sx={{ cursor: 'pointer' }} onClick={() => { setCommandeError(null); setSelectedDevis(d.id); setDetailOpen(true); }}>
                <TableCell>{d.numero}</TableCell>
                <TableCell>{d.clientNom}</TableCell>
                <TableCell>{new Date(d.dateCreation).toLocaleDateString('fr-FR')}</TableCell>
                <TableCell>{new Date(d.dateValidite).toLocaleDateString('fr-FR')}</TableCell>
                <TableCell align="right">{formatMontant(d.montantHT)}</TableCell>
                <TableCell align="right">{formatMontant(d.montantTTC)}</TableCell>
                <TableCell>
                  <Chip label={STATUT_LABELS[d.statut]} size="small" color={STATUT_COLORS[d.statut]} />
                </TableCell>
                <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                  {d.statut === 'BROUILLON' && mesDroits.ecrireCommercial && (
                    <>
                      {/* Remise refusee ou non tranchee : pas d'envoi non plus,
                          elle doit d'abord etre revue a la baisse */}
                      {!d.remiseAValider && (
                        <IconButton size="small" color="primary" title="Envoyer" onClick={() => envoyerMutation.mutate(d.id)}>
                          <SendIcon fontSize="small" />
                        </IconButton>
                      )}
                      <IconButton size="small" color="error" title="Supprimer" onClick={() => deleteMutation.mutate(d.id)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </>
                  )}
                  {d.statut === 'EN_ATTENTE_VALIDATION' && mesDroits.encadrer && (
                    <>
                      <IconButton size="small" color="success" title="Valider la remise (débloque l'envoi)" onClick={() => validerRemiseMutation.mutate(d.id)}>
                        <GavelIcon fontSize="small" />
                      </IconButton>
                      <IconButton size="small" color="error" title="Refuser la remise (-> Brouillon)" onClick={() => refuserRemiseMutation.mutate(d.id)}>
                        <CloseIcon fontSize="small" />
                      </IconButton>
                    </>
                  )}
                  {d.statut === 'ENVOYE' && mesDroits.ecrireCommercial && (
                    <>
                      <IconButton size="small" color="success" title="Accepter" onClick={() => accepterMutation.mutate({ id: d.id })}>
                        <CheckIcon fontSize="small" />
                      </IconButton>
                      <IconButton size="small" color="error" title="Refuser" onClick={() => refuserMutation.mutate({ id: d.id })}>
                        <CloseIcon fontSize="small" />
                      </IconButton>
                    </>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={data?.totalElements ?? 0}
          page={page}
          onPageChange={(_, newPage) => setPage(newPage)}
          rowsPerPage={size}
          onRowsPerPageChange={(e) => { setSize(parseInt(e.target.value, 10)); setPage(0); }}
          rowsPerPageOptions={[10, 25, 50, 100]}
          labelRowsPerPage="Lignes par page"
        />
      </TableContainer>

      {/* Dialog creation devis */}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>{editId !== null ? 'Modifier le devis' : 'Nouveau devis'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Client"
              select
              value={clientId}
              onChange={(e) => setClientId(Number(e.target.value))}
              required
            >
              {clients.map((c) => (
                // Un client bloque ne peut plus recevoir de devis : le serveur
                // le refuse, autant ne pas laisser le commercial saisir toute
                // une affaire pour l'apprendre a l'enregistrement. Le client
                // déjà porte par le devis reste sélectionnable, sinon un devis
                // en cours deviendrait immodifiable.
                <MenuItem
                  key={c.id}
                  value={c.id}
                  disabled={c.statut === 'BLOQUE' && c.id !== clientDuDevis}
                >
                  {c.nom} {c.prenom ?? ''}
                  {c.statut === 'BLOQUE' && (
                    <Chip size="small" color="error" label="bloqué" sx={{ ml: 1 }} />
                  )}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Référence"
              value={reference}
              onChange={(e) => setReference(e.target.value)}
              helperText="Référence dossier/affaire (optionnel)"
            />
            <TextField
              label="Date de validité"
              type="date"
              value={dateValidite}
              onChange={(e) => setDateValidite(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
              required
            />

            <Typography variant="subtitle1">Lignes du devis</Typography>
            {lignes.map((l, i) => (
              <Paper key={i} variant="outlined" sx={{ p: 2 }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                  <Autocomplete
                    size="small"
                    sx={{ minWidth: 320, flex: 1 }}
                    options={produits}
                    value={produits.find((p) => p.id === l.produitId) ?? null}
                    onChange={(_, val) => updateLigne(i, 'produitId', val ? val.id : 0)}
                    getOptionLabel={(p) => `${p.reference} - ${p.designation}`}
                    isOptionEqualToValue={(a, b) => a.id === b.id}
                    noOptionsText="Aucun produit"
                    renderInput={(params) => (
                      <TextField {...params} label="Produit (taper réf. ou désignation)" />
                    )}
                  />
                  <TextField
                    label="Qté"
                    type="number"
                    size="small"
                    value={l.quantite}
                    onChange={(e) => updateLigne(i, 'quantite', parseFloat(e.target.value) || 1)}
                    slotProps={{ htmlInput: { step: 'any', min: 0 } }}
                    sx={{ width: 90 }}
                  />
                  <TextField
                    label="Prix unit."
                    type="number"
                    size="small"
                    disabled={mesDroits.prixImposes}
                    value={l.prixUnitaire}
                    onChange={(e) => updateLigne(i, 'prixUnitaire', parseFloat(e.target.value) || 0)}
                    sx={{ width: 120 }}
                  />
                  <TextField
                    label="TVA %"
                    type="number"
                    size="small"
                    disabled={mesDroits.prixImposes}
                    value={l.tauxTVA}
                    onChange={(e) => updateLigne(i, 'tauxTVA', parseFloat(e.target.value) || 0)}
                    sx={{ width: 80 }}
                  />
                  <TextField
                    label="Remise %"
                    type="number"
                    size="small"
                    value={l.remise ?? 0}
                    onChange={(e) => updateLigne(i, 'remise', parseFloat(e.target.value) || 0)}
                    sx={{ width: 80 }}
                  />
                  <IconButton color="error" size="small" onClick={() => removeLigne(i)}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Stack>
              </Paper>
            ))}
            <Button startIcon={<AddIcon />} onClick={addLigne}>
              Ajouter une ligne
            </Button>
            {formError && <Alert severity="error">{formError}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setDialogOpen(false); resetForm(); }}>Annuler</Button>
          <Button
            variant="contained"
            onClick={handleSubmit}
            disabled={createMutation.isPending || updateMutation.isPending}
          >
            {editId !== null ? 'Enregistrer' : 'Créer'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Dialog detail devis */}
      <Dialog open={detailOpen} onClose={() => { setDetailOpen(false); setSelectedDevis(null); }} fullWidth maxWidth="md">
        <DialogTitle>Devis {detailDevis?.numero}</DialogTitle>
        <DialogContent>
          {detailDevis && (
            <Stack spacing={2} sx={{ mt: 1 }}>
              <Stack direction="row" spacing={2}>
                <Typography><strong>Client :</strong> {detailDevis.clientNom}</Typography>
                <Typography><strong>Statut :</strong> <Chip label={STATUT_LABELS[detailDevis.statut]} size="small" color={STATUT_COLORS[detailDevis.statut]} /></Typography>
                {detailDevis.reference && <Typography><strong>Référence :</strong> {detailDevis.reference}</Typography>}
              </Stack>
              {/* Toutes les dates du devis au meme endroit, dans l'ordre ou
                  elles se sont produites. */}
              <SuiviDates
                titre="Suivi"
                etapes={[
                  { libelle: 'Devis créé', date: detailDevis.dateCreation },
                  {
                    libelle: detailDevis.remiseAValider
                      ? 'Remise refusée' : 'Remise validée',
                    date: detailDevis.dateValidationRemise,
                  },
                  { libelle: 'Envoyé au client', date: detailDevis.dateEnvoi },
                  { libelle: 'Transmis par email', date: detailDevis.dateEnvoiEmail },
                  {
                    libelle: detailDevis.statut === 'REFUSE' ? 'Refusé' : 'Accepté',
                    date: detailDevis.dateReponseClient,
                  },
                  { libelle: 'Fin de validité', date: detailDevis.dateValidite, prevu: true },
                ]}
              />
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Réf.</TableCell>
                      <TableCell>Désignation</TableCell>
                      <TableCell align="right">Qté</TableCell>
                      <TableCell align="right">Prix unit.</TableCell>
                      <TableCell align="right">TVA</TableCell>
                      <TableCell align="right">Remise</TableCell>
                      <TableCell align="right">Montant</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {detailDevis.lignes.map((l) => (
                      <TableRow key={l.id}>
                        <TableCell>{l.reference}</TableCell>
                        <TableCell>{l.designation}</TableCell>
                        <TableCell align="right">{l.quantite}</TableCell>
                        <TableCell align="right">{formatMontant(l.prixUnitaire)}</TableCell>
                        <TableCell align="right">{l.tauxTVA} %</TableCell>
                        <TableCell align="right">{l.remise ?? 0} %</TableCell>
                        <TableCell align="right">{formatMontant(l.montantLigne)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <Stack direction="row" spacing={4} sx={{ justifyContent: 'flex-end' }}>
                <Typography><strong>HT :</strong> {formatMontant(detailDevis.montantHT)}</Typography>
                <Typography><strong>TTC :</strong> {formatMontant(detailDevis.montantTTC)}</Typography>
              </Stack>
              {detailDevis.commentaireClient && (
                <Alert severity="info">Commentaire client : {detailDevis.commentaireClient}</Alert>
              )}

              {/* La reponse donnee par le client via son lien est purement
                  informative : c'est ici, manuellement, que le devis est ensuite
                  accepte ou refuse. Les dates, elles, sont dans le suivi
                  ci-dessus -- les repeter ici brouillait la lecture. */}
              {emailError && <Alert severity="error">{emailError}</Alert>}
              {detailDevis.reponseClient && (
                <Alert
                  severity={detailDevis.reponseClient === 'ACCEPTE' ? 'success' : 'warning'}
                  action={detailDevis.bonCommandeDepose ? (
                    <Button size="small" startIcon={<DownloadIcon />} onClick={() => ouvrirBonCommande(detailDevis.id)}>
                      Bon de commande
                    </Button>
                  ) : undefined}
                >
                  Le client a {detailDevis.reponseClient === 'ACCEPTE' ? 'accepté' : 'refusé'} ce
                  devis depuis son lien. Sa réponse reste à confirmer manuellement ci-dessous.
                </Alert>
              )}
              {/* Message d'echec de creation de commande (client bloque, stock...) */}
              {commandeError && <Alert severity="error">{commandeError}</Alert>}
              {detailDevis.statut === 'ACCEPTE' && aDejaCommande && (
                <Alert severity="info">Une commande à déjà été créée pour ce devis.</Alert>
              )}
              {/* Explique l'absence du bouton d'envoi au client */}
              {detailDevis.remiseAValider && (
                <Alert severity="warning">
                  La remise dépasse le seuil autorisé : le devis attend l'arbitrage du
                  responsable commercial. D'ici là il ne peut être ni envoyé, ni imprimé,
                  ni transmis au client. Baisser la remise le libère.
                </Alert>
              )}
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setDetailOpen(false); setSelectedDevis(null); setCommandeError(null); }}>Fermer</Button>
          {/* Remise non tranchee : ni email ni impression, un PDF circule aussi bien */}
          {detailDevis && !detailDevis.remiseAValider && (
            <Button startIcon={<PictureAsPdfIcon />} onClick={() => ouvrirPdf(detailDevis.id)}>
              PDF
            </Button>
          )}
          {/* Un devis se corrige tant qu'il est au brouillon, et tant qu'il
              attend un arbitrage de remise : c'est le seul moyen d'en sortir. */}
          {(detailDevis?.statut === 'BROUILLON'
            || detailDevis?.statut === 'EN_ATTENTE_VALIDATION') && (
            <Button
              startIcon={<EditIcon />}
              onClick={() => { setDetailOpen(false); ouvrirEdition(detailDevis); }}
            >
              Modifier
            </Button>
          )}
          {detailDevis?.statut === 'BROUILLON' && !detailDevis.remiseAValider && (
            <Button variant="contained" startIcon={<SendIcon />} onClick={() => envoyerMutation.mutate(detailDevis.id)}>
              Envoyer
            </Button>
          )}
          {/* Envoi au client independant du statut : possible sans passer par
              "Envoyer". Seule reserve, la remise : au-dela du seuil elle doit
              etre validee avant que le client ne decouvre le prix, brouillon
              compris. Un renvoi reutilise le meme lien. */}
          {detailDevis && !detailDevis.remiseAValider && mesDroits.ecrireCommercial && (
            <Button
              startIcon={<EmailIcon />}
              onClick={() => emailMutation.mutate(detailDevis.id)}
              disabled={emailMutation.isPending}
            >
              {detailDevis.dateEnvoiEmail ? "Renvoyer l'email" : 'Envoyer au client'}
            </Button>
          )}
          {detailDevis?.statut === 'EN_ATTENTE_VALIDATION' && mesDroits.encadrer && (
            <>
              <Button color="error" startIcon={<CloseIcon />} onClick={() => refuserRemiseMutation.mutate(detailDevis.id)}>
                Refuser la remise
              </Button>
              <Button variant="contained" color="success" startIcon={<GavelIcon />} onClick={() => validerRemiseMutation.mutate(detailDevis.id)}>
                Valider la remise
              </Button>
            </>
          )}
          {/* Decision finale, toujours manuelle, meme si le client a deja repondu */}
          {detailDevis?.statut === 'ENVOYE' && mesDroits.ecrireCommercial && (
            <>
              <Button color="error" startIcon={<CloseIcon />} onClick={() => refuserMutation.mutate({ id: detailDevis.id })}>
                Refuser
              </Button>
              <Button variant="contained" color="success" startIcon={<CheckIcon />} onClick={() => accepterMutation.mutate({ id: detailDevis.id })}>
                Accepter
              </Button>
            </>
          )}
          {/* Bouton masque si une commande existe deja pour ce devis */}
          {detailDevis?.statut === 'ACCEPTE' && !aDejaCommande && mesDroits.ecrireCommercial && (
            <Button
              variant="contained"
              color="success"
              startIcon={<ShoppingCartIcon />}
              onClick={() => commandeMutation.mutate(detailDevis.id)}
              disabled={commandeMutation.isPending}
            >
              Creer commande
            </Button>
          )}
        </DialogActions>
      </Dialog>

      {/* Confirmation de creation de commande */}
      <Snackbar
        open={!!commandeSuccess}
        autoHideDuration={5000}
        onClose={() => setCommandeSuccess(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="success" onClose={() => setCommandeSuccess(null)} sx={{ width: '100%' }}>
          {commandeSuccess}
        </Alert>
      </Snackbar>

      {/* Confirmation d'envoi de l'email au client */}
      <Snackbar
        open={!!emailSuccess}
        autoHideDuration={5000}
        onClose={() => setEmailSuccess(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="success" onClose={() => setEmailSuccess(null)} sx={{ width: '100%' }}>
          {emailSuccess}
        </Alert>
      </Snackbar>
    </Box>
  );
}
