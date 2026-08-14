import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  Grid,
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
import SendIcon from '@mui/icons-material/Send';
import LocalShippingIcon from '@mui/icons-material/LocalShipping';
import InventoryIcon from '@mui/icons-material/Inventory';
import CloseIcon from '@mui/icons-material/Close';
import { listerFournisseurs } from '../api/fournisseurs';
import { listerProduits } from '../api/produits';
import { listerDepots } from '../api/depots';
import {
  annulerCommandeFournisseur,
  changerStatutCommandeFournisseur,
  creerCommandeFournisseur,
  emettreCommandeFournisseur,
  listerCommandesFournisseur,
  modifierCommandeFournisseur,
  receptionnerCommandeFournisseur,
  supprimerCommandeFournisseur,
  type CommandeFournisseur,
  type CommandeFournisseurRequest,
  type Incoterm,
  type LigneCommandeFournisseur,
  type ModeTransport,
  type StatutCommandeFournisseur,
} from '../api/commandesFournisseur';
import { formatMontant, formatNombre } from '../utils/format';
import SuiviDates from '../components/SuiviDates';

const STATUT_LABELS: Record<StatutCommandeFournisseur, string> = {
  BROUILLON: 'Brouillon',
  COMMANDEE: 'Commandée',
  EN_TRANSIT: 'En transit',
  EN_DOUANE: 'En douane',
  RECEPTIONNEE_PARTIELLEMENT: 'Reçue partiellement',
  RECEPTIONNEE: 'Réceptionnée',
  ANNULEE: 'Annulée',
};

const STATUT_COLORS: Record<StatutCommandeFournisseur,
  'default' | 'info' | 'primary' | 'secondary' | 'success' | 'error' | 'warning'> = {
  BROUILLON: 'default',
  COMMANDEE: 'info',
  EN_TRANSIT: 'primary',
  EN_DOUANE: 'secondary',
  RECEPTIONNEE_PARTIELLEMENT: 'warning',
  RECEPTIONNEE: 'success',
  ANNULEE: 'error',
};

const INCOTERMS: Incoterm[] = ['EXW', 'FOB', 'CFR', 'CIF', 'DAP', 'DDP'];
const MODES_TRANSPORT: ModeTransport[] = ['MARITIME', 'AERIEN', 'ROUTIER'];
const DEVISES = ['MAD', 'EUR', 'USD'];

/** Ligne en cours de saisie : les nombres restent des chaines tant qu'on tape. */
interface LigneSaisie {
  produitId: number;
  reference: string;
  designation: string;
  quantite: string;
  prix: string;
}

const FORM_VIDE = {
  fournisseurId: 0,
  depotReceptionCode: '',
  dateCommande: '',
  dateArriveePrevue: '',
  devise: 'EUR',
  tauxChange: '',
  incoterm: '' as Incoterm | '',
  modeTransport: '' as ModeTransport | '',
  paysOrigine: '',
  fraisTransportEnDevise: false,
  transporteur: '',
  referenceTransport: '',
  portArrivee: '',
  fraisFret: '',
  fraisAssurance: '',
  droitsDouane: '',
  fraisTransit: '',
  observations: '',
};

const messageErreur = (e: unknown, defaut: string) =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? defaut;

const nombreOuNull = (valeur: string) =>
  valeur.trim() === '' ? null : Number(valeur);

export default function CommandesFournisseurPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [erreur, setErreur] = useState<string | null>(null);
  const [succes, setSucces] = useState<string | null>(null);

  // --- Saisie ---
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [form, setForm] = useState({ ...FORM_VIDE });
  const [lignes, setLignes] = useState<LigneSaisie[]>([]);
  const [formErreur, setFormErreur] = useState<string | null>(null);

  // --- Détail et réception ---
  const [detail, setDetail] = useState<CommandeFournisseur | null>(null);
  const [recues, setRecues] = useState<Record<number, string>>({});

  const { data, isLoading, isError } = useQuery({
    queryKey: ['commandes-fournisseur', page, size],
    queryFn: () => listerCommandesFournisseur(page, size),
  });
  const { data: fournisseurs } = useQuery({
    queryKey: ['fournisseurs-liste'],
    queryFn: () => listerFournisseurs({ size: 200 }),
  });
  const { data: produits } = useQuery({
    queryKey: ['produits-liste-achat'],
    queryFn: () => listerProduits({ size: 500, sort: 'reference,asc' }),
  });
  const { data: depots } = useQuery({ queryKey: ['depots-list'], queryFn: listerDepots });

  const rafraichir = () => {
    queryClient.invalidateQueries({ queryKey: ['commandes-fournisseur'] });
    queryClient.invalidateQueries({ queryKey: ['tableau-de-bord'] });
  };

  const echec = (defaut: string) => (e: unknown) => {
    setSucces(null);
    setErreur(messageErreur(e, defaut));
  };

  const enregistrerMutation = useMutation({
    mutationFn: (payload: CommandeFournisseurRequest) =>
      editId === null
        ? creerCommandeFournisseur(payload)
        : modifierCommandeFournisseur(editId, payload),
    onSuccess: (c) => {
      rafraichir();
      fermerDialog();
      setErreur(null);
      setSucces(`Commande ${c.numero} enregistrée`);
    },
    onError: (e) => setFormErreur(messageErreur(e, 'Enregistrement impossible')),
  });

  const emettreMutation = useMutation({
    mutationFn: emettreCommandeFournisseur,
    onSuccess: (c) => {
      rafraichir();
      setDetail(c);
      setErreur(null);
      setSucces(`Bon de commande ${c.numero} emis`);
    },
    onError: echec('Émission impossible'),
  });

  const statutMutation = useMutation({
    mutationFn: ({ id, statut }: { id: number; statut: StatutCommandeFournisseur }) =>
      changerStatutCommandeFournisseur(id, statut),
    onSuccess: (c) => { rafraichir(); setDetail(c); setErreur(null); },
    onError: echec('Changement de statut impossible'),
  });

  const receptionMutation = useMutation({
    mutationFn: ({ id, lignesRecues }: {
      id: number; lignesRecues: { ligneId: number; quantiteRecue: number }[];
    }) => receptionnerCommandeFournisseur(id, lignesRecues),
    onSuccess: (c) => {
      rafraichir();
      queryClient.invalidateQueries({ queryKey: ['stock'] });
      setDetail(c);
      // La saisie est close : les quantités redeviennent une lecture.
      setRecues({});
      setErreur(null);
      setSucces(c.statut === 'RECEPTIONNEE_PARTIELLEMENT'
        ? `${c.numero} : livraison partielle enregistrée, le reliquat reste attendu`
        : `${c.numero} réceptionnée : le stock est crédite`);
    },
    onError: echec('Réception impossible'),
  });

  const annulerMutation = useMutation({
    mutationFn: annulerCommandeFournisseur,
    onSuccess: (c) => { rafraichir(); setDetail(c); setErreur(null); },
    onError: echec('Annulation impossible'),
  });

  const supprimerMutation = useMutation({
    mutationFn: supprimerCommandeFournisseur,
    onSuccess: () => { rafraichir(); setDetail(null); setErreur(null); setSucces('Commande supprimée'); },
    onError: echec('Suppression impossible'),
  });

  const fermerDialog = () => {
    setDialogOpen(false);
    setEditId(null);
    setForm({ ...FORM_VIDE });
    setLignes([]);
    setFormErreur(null);
  };

  const ouvrirCreation = () => {
    setEditId(null);
    setForm({ ...FORM_VIDE, depotReceptionCode: depots?.[0]?.code ?? '' });
    setLignes([]);
    setFormErreur(null);
    setDialogOpen(true);
  };

  const ouvrirEdition = (c: CommandeFournisseur) => {
    setEditId(c.id);
    setForm({
      fournisseurId: c.fournisseurId,
      depotReceptionCode: c.depotReceptionCode,
      dateCommande: c.dateCommande ?? '',
      dateArriveePrevue: c.dateArriveePrevue ?? '',
      devise: c.devise ?? 'EUR',
      tauxChange: c.tauxChange != null ? String(c.tauxChange) : '',
      incoterm: c.incoterm ?? '',
      modeTransport: c.modeTransport ?? '',
      paysOrigine: c.paysOrigine ?? '',
      fraisTransportEnDevise: c.fraisTransportEnDevise,
      transporteur: c.transporteur ?? '',
      referenceTransport: c.referenceTransport ?? '',
      portArrivee: c.portArrivee ?? '',
      fraisFret: c.fraisFret != null ? String(c.fraisFret) : '',
      fraisAssurance: c.fraisAssurance != null ? String(c.fraisAssurance) : '',
      droitsDouane: c.droitsDouane != null ? String(c.droitsDouane) : '',
      fraisTransit: c.fraisTransit != null ? String(c.fraisTransit) : '',
      observations: c.observations ?? '',
    });
    setLignes(c.lignes.map((l) => ({
      produitId: l.produitId,
      reference: l.reference,
      designation: l.designation,
      quantite: String(l.quantiteCommandee),
      prix: l.prixUnitaireDevise != null ? String(l.prixUnitaireDevise) : '',
    })));
    setFormErreur(null);
    setDetail(null);
    setDialogOpen(true);
  };

  const soumettre = () => {
    if (!form.fournisseurId || !form.depotReceptionCode) {
      setFormErreur('Le fournisseur et le dépôt de réception sont obligatoires');
      return;
    }
    if (lignes.length === 0) {
      setFormErreur("Une commande sans ligne n'a rien à commander");
      return;
    }
    if (lignes.some((l) => !l.quantite || Number(l.quantite) <= 0)) {
      setFormErreur('Chaque ligne demande une quantité strictement positive');
      return;
    }
    // Le dirham n'a pas de taux a saisir : il vaut 1 par definition.
    if (form.devise !== 'MAD' && !form.tauxChange) {
      setFormErreur('Un achat en devise etrangere demande un taux de change');
      return;
    }
    enregistrerMutation.mutate({
      fournisseurId: form.fournisseurId,
      depotReceptionCode: form.depotReceptionCode,
      dateCommande: form.dateCommande || null,
      dateArriveePrevue: form.dateArriveePrevue || null,
      devise: form.devise,
      tauxChange: form.devise === 'MAD' ? null : Number(form.tauxChange),
      incoterm: form.incoterm === '' ? null : form.incoterm,
      modeTransport: form.modeTransport === '' ? null : form.modeTransport,
      paysOrigine: form.paysOrigine || null,
      fraisTransportEnDevise: form.fraisTransportEnDevise,
      transporteur: form.transporteur || null,
      referenceTransport: form.referenceTransport || null,
      portArrivee: form.portArrivee || null,
      fraisFret: nombreOuNull(form.fraisFret),
      fraisAssurance: nombreOuNull(form.fraisAssurance),
      droitsDouane: nombreOuNull(form.droitsDouane),
      fraisTransit: nombreOuNull(form.fraisTransit),
      observations: form.observations || null,
      lignes: lignes.map((l) => ({
        produitId: l.produitId,
        quantiteCommandee: Number(l.quantite),
        prixUnitaireDevise: nombreOuNull(l.prix),
      })),
    });
  };

  const ouvrirReception = (c: CommandeFournisseur) => {
    setDetail(c);
    // Par defaut tout le reliquat arrive : c'est le cas courant, et une
    // livraison partielle se corrige ligne par ligne.
    setRecues(Object.fromEntries(c.lignes.map((l) => [l.id, String(reliquat(l))])));
  };

  /** Ce qui reste a recevoir sur une ligne. */
  const reliquat = (l: LigneCommandeFournisseur) =>
    l.quantiteCommandee - (l.quantiteRecue ?? 0);

  const enReception = Object.keys(recues).length > 0;
  const receptionnable = detail !== null && (detail.statut === 'COMMANDEE'
    || detail.statut === 'EN_TRANSIT' || detail.statut === 'EN_DOUANE'
    || detail.statut === 'RECEPTIONNEE_PARTIELLEMENT');

  const totalDevise = lignes.reduce(
    (somme, l) => somme + (Number(l.quantite) || 0) * (Number(l.prix) || 0), 0);
  const taux = form.devise === 'MAD' ? 1 : Number(form.tauxChange) || 0;
  // Fret et assurance peuvent être libellés en devise ; douane et transit jamais.
  const fraisTransport = (Number(form.fraisFret) || 0) + (Number(form.fraisAssurance) || 0);
  const totalFrais = (form.fraisTransportEnDevise ? fraisTransport * taux : fraisTransport)
    + (Number(form.droitsDouane) || 0) + (Number(form.fraisTransit) || 0);

  return (
    <Box>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4">Commandes fournisseur</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={ouvrirCreation}>
          Nouvelle commande
        </Button>
      </Stack>

      {erreur && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setErreur(null)}>{erreur}</Alert>}
      {succes && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSucces(null)}>{succes}</Alert>}
      {isError && <Alert severity="error" sx={{ mb: 2 }}>Erreur de chargement des commandes</Alert>}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Numéro</TableCell>
              <TableCell>Fournisseur</TableCell>
              <TableCell>Dépôt</TableCell>
              <TableCell>Arrivée prévue</TableCell>
              <TableCell align="right">Montant</TableCell>
              <TableCell align="right">Coût total</TableCell>
              <TableCell>Statut</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && <TableRow><TableCell colSpan={7}>Chargement...</TableCell></TableRow>}
            {(data?.content ?? []).map((c) => (
              <TableRow
                key={c.id}
                hover
                sx={{ cursor: 'pointer' }}
                onClick={() => { setDetail(c); setRecues({}); }}
              >
                <TableCell><strong>{c.numero}</strong></TableCell>
                <TableCell>{c.fournisseurNom}</TableCell>
                <TableCell><Chip size="small" label={c.depotReceptionCode} /></TableCell>
                <TableCell>{c.dateArriveePrevue
                  ? new Date(c.dateArriveePrevue).toLocaleDateString('fr-FR') : '-'}</TableCell>
                <TableCell align="right">
                  {c.montantDevise != null
                    ? `${formatNombre(c.montantDevise)} ${c.devise}` : '-'}
                </TableCell>
                <TableCell align="right">
                  {c.coutTotalMAD != null ? formatMontant(c.coutTotalMAD) : '-'}
                </TableCell>
                <TableCell>
                  <Chip size="small" label={STATUT_LABELS[c.statut]} color={STATUT_COLORS[c.statut]} />
                </TableCell>
              </TableRow>
            ))}
            {data && data.content.length === 0 && (
              <TableRow><TableCell colSpan={7}>Aucune commande fournisseur.</TableCell></TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={data?.totalElements ?? 0}
          page={page}
          onPageChange={(_, p) => setPage(p)}
          rowsPerPage={size}
          onRowsPerPageChange={(e) => { setSize(parseInt(e.target.value, 10)); setPage(0); }}
          rowsPerPageOptions={[10, 25, 50]}
          labelRowsPerPage="Lignes par page"
        />
      </TableContainer>

      {/* Saisie : creation ou correction tant que le bon n'est pas emis */}
      <Dialog open={dialogOpen} onClose={fermerDialog} fullWidth maxWidth="lg">
        <DialogTitle>
          {editId === null ? 'Nouvelle commande fournisseur' : 'Modifier la commande'}
        </DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                label="Fournisseur" select fullWidth required
                value={form.fournisseurId || ''}
                onChange={(e) => setForm({ ...form, fournisseurId: Number(e.target.value) })}
              >
                {(fournisseurs?.content ?? []).map((f) => (
                  <MenuItem key={f.id} value={f.id}>{f.nom}</MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                label="Dépôt de réception" select fullWidth required
                value={form.depotReceptionCode}
                onChange={(e) => setForm({ ...form, depotReceptionCode: e.target.value })}
              >
                {(depots ?? []).map((d) => (
                  <MenuItem key={d.id} value={d.code}>{d.code}</MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField
                label="Date de commande" type="date" fullWidth
                value={form.dateCommande}
                onChange={(e) => setForm({ ...form, dateCommande: e.target.value })}
                slotProps={{ inputLabel: { shrink: true } }}
                helperText="Vide : la date d'émission"
              />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField
                label="Arrivée prévue" type="date" fullWidth
                value={form.dateArriveePrevue}
                onChange={(e) => setForm({ ...form, dateArriveePrevue: e.target.value })}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            </Grid>
          </Grid>

          <Divider sx={{ my: 2 }} />
          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
            Import · laisser vide pour un achat local en dirhams
          </Typography>
          <Grid container spacing={2}>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField
                label="Devise" select fullWidth value={form.devise}
                onChange={(e) => setForm({ ...form, devise: e.target.value })}
              >
                {DEVISES.map((d) => <MenuItem key={d} value={d}>{d}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField
                label="Taux de change" type="number" fullWidth
                value={form.devise === 'MAD' ? '1' : form.tauxChange}
                disabled={form.devise === 'MAD'}
                onChange={(e) => setForm({ ...form, tauxChange: e.target.value })}
                helperText={form.devise === 'MAD' ? 'Dirham : taux 1' : 'vers le dirham'}
              />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField
                label="Incoterm" select fullWidth value={form.incoterm}
                onChange={(e) => setForm({ ...form, incoterm: e.target.value as Incoterm })}
              >
                <MenuItem value="">-</MenuItem>
                {INCOTERMS.map((i) => <MenuItem key={i} value={i}>{i}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField
                label="Mode de transport" select fullWidth value={form.modeTransport}
                onChange={(e) => setForm({ ...form, modeTransport: e.target.value as ModeTransport })}
              >
                <MenuItem value="">-</MenuItem>
                {MODES_TRANSPORT.map((m) => <MenuItem key={m} value={m}>{m}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField label="Pays d'origine" fullWidth value={form.paysOrigine}
                onChange={(e) => setForm({ ...form, paysOrigine: e.target.value })}
                helperText="Détermine les droits de douane" />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField label="Transporteur" fullWidth value={form.transporteur}
                onChange={(e) => setForm({ ...form, transporteur: e.target.value })} />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField label="Conteneur / BL" fullWidth value={form.referenceTransport}
                onChange={(e) => setForm({ ...form, referenceTransport: e.target.value })} />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField label="Port d'arrivée" fullWidth value={form.portArrivee}
                onChange={(e) => setForm({ ...form, portArrivee: e.target.value })} />
            </Grid>
          </Grid>

          <Divider sx={{ my: 2 }} />
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 1 }}>
            <Typography variant="subtitle2" color="text.secondary">
              Frais du dossier · répartis sur les lignes à la réception
            </Typography>
            {/* Le transitaire etranger facture souvent en devise, alors que douane
                et transit sont toujours percus en dirhams */}
            <FormControlLabel
              control={
                <Checkbox
                  size="small"
                  checked={form.fraisTransportEnDevise}
                  disabled={form.devise === 'MAD'}
                  onChange={(e) => setForm({ ...form, fraisTransportEnDevise: e.target.checked })}
                />
              }
              label={
                <Typography variant="caption">
                  Fret et assurance en {form.devise} (convertis au taux du dossier)
                </Typography>
              }
            />
          </Stack>
          <Grid container spacing={2}>
            {([['fraisFret', 'Fret', true], ['fraisAssurance', 'Assurance', true],
               ['droitsDouane', 'Droits de douane', false],
               ['fraisTransit', 'Transit et manutention', false]] as [string, string, boolean][]
            ).map(([champ, libelle, transport]) => (
              <Grid key={champ} size={{ xs: 6, md: 3 }}>
                <TextField
                  label={`${libelle} (${
                    transport && form.fraisTransportEnDevise ? form.devise : 'DH'})`}
                  type="number" fullWidth
                  value={form[champ as keyof typeof form] as string}
                  onChange={(e) => setForm({ ...form, [champ]: e.target.value })}
                />
              </Grid>
            ))}
          </Grid>

          <Divider sx={{ my: 2 }} />
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
            <Typography variant="subtitle2" color="text.secondary">Lignes</Typography>
            <Autocomplete
              size="small"
              sx={{ width: 380 }}
              options={produits?.content ?? []}
              getOptionLabel={(p) => `${p.reference} · ${p.designation}`}
              value={null}
              onChange={(_, produit) => {
                if (!produit) return;
                setLignes([...lignes, {
                  produitId: produit.id,
                  reference: produit.reference,
                  designation: produit.designation,
                  quantite: '1',
                  prix: '',
                }]);
              }}
              renderInput={(params) => <TextField {...params} label="Ajouter un produit" />}
            />
          </Stack>

          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Produit</TableCell>
                  <TableCell sx={{ width: 120 }}>Quantité</TableCell>
                  <TableCell sx={{ width: 160 }}>Prix unitaire ({form.devise})</TableCell>
                  <TableCell align="right" sx={{ width: 140 }}>Montant</TableCell>
                  <TableCell sx={{ width: 60 }} />
                </TableRow>
              </TableHead>
              <TableBody>
                {lignes.map((l, index) => (
                  <TableRow key={`${l.produitId}-${index}`}>
                    <TableCell>
                      <strong>{l.reference}</strong> · {l.designation}
                    </TableCell>
                    <TableCell>
                      <TextField
                        size="small" type="number" value={l.quantite}
                        onChange={(e) => majLigne(index, { quantite: e.target.value })}
                      />
                    </TableCell>
                    <TableCell>
                      <TextField
                        size="small" type="number" value={l.prix}
                        onChange={(e) => majLigne(index, { prix: e.target.value })}
                      />
                    </TableCell>
                    <TableCell align="right">
                      {formatNombre((Number(l.quantite) || 0) * (Number(l.prix) || 0))}
                    </TableCell>
                    <TableCell>
                      <IconButton size="small" color="error"
                        onClick={() => setLignes(lignes.filter((_, i) => i !== index))}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))}
                {lignes.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5}>
                      <Typography variant="body2" color="text.secondary">
                        Ajoutez au moins un produit.
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>

          <Stack direction="row" spacing={4} sx={{ justifyContent: 'flex-end', mt: 2 }}>
            <Typography variant="body2">
              Marchandise : <strong>{formatNombre(totalDevise)} {form.devise}</strong>
            </Typography>
            <Typography variant="body2">
              Frais : <strong>{formatMontant(totalFrais)}</strong>
            </Typography>
            <Typography variant="body2">
              Cout total : <strong>{formatMontant(totalDevise * taux + totalFrais)}</strong>
            </Typography>
          </Stack>

          <TextField
            label="Observations" fullWidth multiline rows={2} sx={{ mt: 2 }}
            value={form.observations}
            onChange={(e) => setForm({ ...form, observations: e.target.value })}
          />
          {formErreur && <Alert severity="error" sx={{ mt: 2 }}>{formErreur}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={fermerDialog}>Annuler</Button>
          <Button variant="contained" onClick={soumettre} disabled={enregistrerMutation.isPending}>
            Enregistrer
          </Button>
        </DialogActions>
      </Dialog>

      {/* Detail : avancement du dossier et reception */}
      <Dialog open={detail !== null} onClose={() => setDetail(null)} fullWidth maxWidth="md">
        {detail && (
          <>
            <DialogTitle>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <span>{detail.numero}</span>
                <Chip size="small" label={STATUT_LABELS[detail.statut]}
                  color={STATUT_COLORS[detail.statut]} />
              </Stack>
            </DialogTitle>
            <DialogContent>
              <Grid container spacing={1.5} sx={{ mb: 2 }}>
                <Info libelle="Fournisseur" valeur={detail.fournisseurNom} />
                <Info libelle="Dépôt de réception" valeur={detail.depotReceptionCode} />
                <Info libelle="Acheteur" valeur={detail.acheteurNom ?? '-'} />
                <Info libelle="Incoterm" valeur={detail.incoterm ?? '-'} />
                <Info libelle="Mode de transport" valeur={detail.modeTransport ?? '-'} />
                <Info libelle="Pays d'origine" valeur={detail.paysOrigine ?? '-'} />
                <Info libelle="Transport" valeur={[detail.transporteur, detail.referenceTransport]
                  .filter(Boolean).join(' · ') || '-'} />
                <Info libelle="Marchandise" valeur={detail.montantDevise != null
                  ? `${formatNombre(detail.montantDevise)} ${detail.devise}` : '-'} />
                <Info libelle="Frais du dossier" valeur={formatMontant(detail.totalFrais ?? 0)} />
                <Info libelle="Coût total" valeur={detail.coutTotalMAD != null
                  ? formatMontant(detail.coutTotalMAD) : '-'} />
              </Grid>

              {/* Le dossier d'import se lit dans ses dates : delai reel du
                  fournisseur, temps passe en douane, etalement des livraisons. */}
              <SuiviDates
                titre="Suivi du dossier"
                etapes={[
                  { libelle: 'Dossier créé', date: detail.dateCreation },
                  { libelle: 'Bon de commande émis', date: detail.dateCommande },
                  { libelle: 'Marchandise embarquée', date: detail.dateTransit },
                  { libelle: 'Arrivée en douane', date: detail.dateDouane },
                  { libelle: 'Première livraison', date: detail.datePremiereReception },
                  { libelle: 'Réceptionnée en totalite', date: detail.dateReception },
                  { libelle: 'Annulée', date: detail.dateAnnulation },
                  { libelle: 'Arrivée annoncée', date: detail.dateArriveePrevue, prevu: true },
                ]}
              />
              <Divider sx={{ my: 2 }} />

              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Produit</TableCell>
                      <TableCell>Réf. fournisseur</TableCell>
                      <TableCell align="right">Commande</TableCell>
                      <TableCell align="right">Déjà reçu</TableCell>
                      {enReception && <TableCell align="right">Cette livraison</TableCell>}
                      <TableCell align="right">Coût unitaire</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {detail.lignes.map((l) => (
                      <TableRow key={l.id}>
                        <TableCell><strong>{l.reference}</strong> · {l.designation}</TableCell>
                        <TableCell>{l.referenceFournisseur ?? '-'}</TableCell>
                        <TableCell align="right">{l.quantiteCommandee}</TableCell>
                        <TableCell align="right">{l.quantiteRecue ?? '-'}</TableCell>
                        {enReception && (
                          <TableCell align="right">
                            {/* On saisit ce qui arrive maintenant, pas le cumul */}
                            <TextField
                              size="small" type="number" sx={{ width: 110 }}
                              value={recues[l.id] ?? ''}
                              onChange={(e) => setRecues({ ...recues, [l.id]: e.target.value })}
                              helperText={`reste ${reliquat(l)}`}
                            />
                          </TableCell>
                        )}
                        <TableCell align="right">
                          {l.coutUnitaireMAD != null
                            ? formatMontant(l.coutUnitaireMAD) : '-'}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>

              {detail.observations && (
                <Alert severity="info" sx={{ mt: 2 }}>{detail.observations}</Alert>
              )}
              {detail.statut === 'RECEPTIONNEE_PARTIELLEMENT' && !enReception && (
                <Alert severity="warning" sx={{ mt: 2 }}>
                  Livraison partielle : {detail.lignes.filter((l) => reliquat(l) > 0).length} ligne(s)
                  attendent encore du reliquat. Le stock déjà reçu est crédité.
                </Alert>
              )}
              {detail.statut === 'RECEPTIONNEE' && (
                <Alert severity="success" sx={{ mt: 2 }}>
                  Le stock du depot {detail.depotReceptionCode} a ete credite et le cout de
                  revient des produits recalcule.
                </Alert>
              )}
            </DialogContent>
            <DialogActions>
              <Button onClick={() => { setDetail(null); setRecues({}); }}>Fermer</Button>

              {detail.statut === 'BROUILLON' && (
                <>
                  <Button color="error" startIcon={<DeleteIcon />}
                    onClick={() => supprimerMutation.mutate(detail.id)}>
                    Supprimer
                  </Button>
                  <Button onClick={() => ouvrirEdition(detail)}>Modifier</Button>
                  <Button variant="contained" startIcon={<SendIcon />}
                    disabled={emettreMutation.isPending}
                    onClick={() => emettreMutation.mutate(detail.id)}>
                    Emettre le bon de commande
                  </Button>
                </>
              )}

              {/* Avancement du transit */}
              {(detail.statut === 'COMMANDEE' || detail.statut === 'EN_TRANSIT') && (
                <Button startIcon={<LocalShippingIcon />}
                  onClick={() => statutMutation.mutate({
                    id: detail.id,
                    statut: detail.statut === 'COMMANDEE' ? 'EN_TRANSIT' : 'EN_DOUANE',
                  })}>
                  {detail.statut === 'COMMANDEE' ? 'Marquer en transit' : 'Marquer en douane'}
                </Button>
              )}

              {/* Une fois qu'une partie est entree en stock, seul le reliquat se pilote */}
              {(detail.statut === 'COMMANDEE' || detail.statut === 'EN_TRANSIT'
                || detail.statut === 'EN_DOUANE') && (
                <Button color="error" startIcon={<CloseIcon />}
                  onClick={() => annulerMutation.mutate(detail.id)}>
                  Annuler
                </Button>
              )}

              {receptionnable && (
                !enReception ? (
                  <Tooltip title="Saisir ce qui est réellement arrivé">
                    <Button variant="contained" startIcon={<InventoryIcon />}
                      onClick={() => ouvrirReception(detail)}>
                      {detail.statut === 'RECEPTIONNEE_PARTIELLEMENT'
                        ? 'Réceptionner le reliquat' : 'Réceptionner'}
                    </Button>
                  </Tooltip>
                ) : (
                  <Button variant="contained" color="success" startIcon={<InventoryIcon />}
                    disabled={receptionMutation.isPending}
                    onClick={() => receptionMutation.mutate({
                      id: detail.id,
                      lignesRecues: detail.lignes.map((l) => ({
                        ligneId: l.id,
                        quantiteRecue: Number(recues[l.id] ?? reliquat(l)),
                      })),
                    })}>
                    Confirmer la réception
                  </Button>
                )
              )}
            </DialogActions>
          </>
        )}
      </Dialog>
    </Box>
  );

  function majLigne(index: number, champs: Partial<LigneSaisie>) {
    setLignes(lignes.map((l, i) => (i === index ? { ...l, ...champs } : l)));
  }
}

function Info({ libelle, valeur }: { libelle: string; valeur: string }) {
  return (
    <Grid size={{ xs: 12, sm: 6, md: 4 }}>
      <Typography variant="caption" color="text.secondary">{libelle}</Typography>
      <Typography variant="body2"><strong>{valeur}</strong></Typography>
    </Grid>
  );
}
