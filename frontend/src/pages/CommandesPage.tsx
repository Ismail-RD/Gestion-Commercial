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
import IconeRubrique from '@mui/icons-material/ShoppingCart';
import EnTetePage from '../components/EnTetePage';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import EditIcon from '@mui/icons-material/Edit';
import LocalShippingIcon from '@mui/icons-material/LocalShipping';
import ChecklistIcon from '@mui/icons-material/Checklist';
import GavelIcon from '@mui/icons-material/Gavel';
import {
  changerStatutCommande,
  validerRemiseCommande,
  creerCommande,
  listerCommandes,
  modifierCommande,
  supprimerCommande,
  getCommande,
  telechargerBonLivraison,
  telechargerBonPreparation,
  validerCommande,
  type CommandeQuery,
} from '../api/commandes';
import { listerClients } from '../api/clients';
import { getDevis } from '../api/devis';
import { listerProduits } from '../api/produits';
import { apercuStock } from '../api/stock';
import { listerDepots } from '../api/depots';
import type { Commande, Produit, StatutCommande } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { droits } from '../auth/droits';
import { formatMontant } from '../utils/format';
import SuiviDates from '../components/SuiviDates';

const STATUT_COLORS: Record<StatutCommande, 'default' | 'info' | 'primary' | 'success' | 'error' | 'secondary'> = {
  EN_ATTENTE_VALIDATION: 'secondary',
  EN_ATTENTE: 'default',
  VALIDEE: 'info',
  EN_PREPARATION: 'primary',
  LIVREE: 'success',
  ANNULEE: 'error',
};

const STATUT_LABELS: Record<StatutCommande, string> = {
  EN_ATTENTE_VALIDATION: 'Remise à valider',
  EN_ATTENTE: 'En attente',
  VALIDEE: 'Validée',
  EN_PREPARATION: 'En préparation',
  LIVREE: 'Livrée',
  ANNULEE: 'Annulée',
};

/** Ligne en cours d'edition ; la quantité reste une chaine tant qu'on la saisit. */
interface LigneEditee {
  produitId: number;
  reference: string;
  designation: string;
  quantite: string;
  /** Conditions negociees, pre-remplies depuis le devis d'origine ou le catalogue. */
  prixUnitaire: number;
  tauxTVA: number;
  remise: number;
  depotCode: string;
  /** Deja presente sur la commande avant l'edition (donc deja reservee en stock). */
  existante: boolean;
}

/** Montant HT de la ligne, remise deduite : meme calcul que le backend. */
const montantLigne = (l: LigneEditee) =>
  (Number(l.quantite) || 0) * l.prixUnitaire * (1 - (l.remise || 0) / 100);

export default function CommandesPage() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const mesDroits = droits(user?.role);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [recherche, setRecherche] = useState('');
  const [statutFilter, setStatutFilter] = useState('');
  const [dateMin, setDateMin] = useState('');
  const [dateMax, setDateMax] = useState('');
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const query: CommandeQuery = {
    page,
    size,
    sort: 'dateCommande,desc',
    recherche: recherche || undefined,
    statut: (statutFilter as StatutCommande) || undefined,
    dateMin: dateMin || undefined,
    dateMax: dateMax || undefined,
  };

  const { data, isLoading, isError } = useQuery({
    queryKey: ['commandes', query],
    queryFn: () => listerCommandes(query),
  });

  const { data: detailCommande } = useQuery({
    queryKey: ['commande-detail', selectedId],
    queryFn: () => getCommande(selectedId!),
    enabled: selectedId !== null,
  });

  const deleteMutation = useMutation({
    mutationFn: supprimerCommande,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['commandes'] }),
  });

  const invaliderTout = () => {
    queryClient.invalidateQueries({ queryKey: ['commandes'] });
    queryClient.invalidateQueries({ queryKey: ['commande-detail'] });
    queryClient.invalidateQueries({ queryKey: ['stock'] });
    queryClient.invalidateQueries({ queryKey: ['stock-apercu'] });
  };

  const statutMutation = useMutation({
    mutationFn: ({ id, statut }: { id: number; statut: StatutCommande }) => changerStatutCommande(id, statut),
    onSuccess: invaliderTout,
  });

  const validerRemiseMutation = useMutation({
    mutationFn: validerRemiseCommande,
    onSuccess: invaliderTout,
  });

  // --- Validation avec choix des dépôts ---
  const [validationCommande, setValidationCommande] = useState<Commande | null>(null);
  // ligneId -> code de dépôt choisi ('' = non choisi)
  const [depotsLignes, setDepotsLignes] = useState<Record<number, string>>({});
  const [validationErreur, setValidationErreur] = useState<string | null>(null);

  const { data: depots } = useQuery({ queryKey: ['depots-list'], queryFn: listerDepots });

  // Stock de tous les produits (produitId -> dépôtCode -> disponible), pour guider
  // le choix : ce qui est déjà réservé par une autre commande n'est pas proposable.
  const { data: apercu } = useQuery({
    queryKey: ['stock-apercu', 'validation'],
    queryFn: () => apercuStock({ size: 500 }),
    enabled: validationCommande !== null,
  });
  const stockParProduit: Record<number, Record<string, number>> = {};
  (apercu?.content ?? []).forEach((a) => {
    stockParProduit[a.produitId] = Object.fromEntries(
      a.depots.map((d) => [d.depotCode, d.disponible ?? d.quantite]),
    );
  });

  const ouvrirValidation = (c: Commande) => {
    setValidationErreur(null);
    setDepotsLignes(Object.fromEntries(c.lignes.map((l) => [l.id, '' as const])));
    setValidationCommande(c);
  };

  const validerMutation = useMutation({
    mutationFn: (c: Commande) =>
      validerCommande(
        c.id,
        c.lignes.map((l) => ({ ligneId: l.id, depotCode: depotsLignes[l.id] })),
      ),
    onSuccess: () => {
      invaliderTout();
      setValidationCommande(null);
    },
    onError: (e: unknown) => {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Validation impossible';
      setValidationErreur(msg);
    },
  });

  // --- Saisie d'une commande : création directe ou retouche d'une existante ---
  // Une commande déjà validée a réservé son stock : un produit ajoute a ce
  // moment-la doit indiquer de quel dépôt il sort.
  const [dialogOpen, setDialogOpen] = useState(false);
  // Commande en cours de correction ; null = création directe (sans devis)
  const [editionCommande, setEditionCommande] = useState<Commande | null>(null);
  const [clientEdite, setClientEdite] = useState<number>(0);
  const [lignesEditees, setLignesEditees] = useState<LigneEditee[]>([]);
  const [editionErreur, setEditionErreur] = useState<string | null>(null);

  const stockDejaSorti =
    editionCommande?.statut === 'VALIDEE' || editionCommande?.statut === 'EN_PREPARATION';
  // Le client est fige des que la commande decoule d'un devis ou qu'elle est validée
  const clientFige =
    editionCommande !== null
    && (editionCommande.devisId != null || editionCommande.statut !== 'EN_ATTENTE');

  // Tout le catalogue est ajoutable : la commande n'est pas limitee au devis.
  const { data: produitsCatalogue } = useQuery({
    queryKey: ['produits-list'],
    queryFn: () => listerProduits({ size: 1000, sort: 'reference,asc' }),
    enabled: dialogOpen,
  });

  const { data: clientsData } = useQuery({
    queryKey: ['clients-list'],
    queryFn: () => listerClients({ size: 500, sort: 'nom,asc' }),
    enabled: dialogOpen,
  });

  const ouvrirCreation = () => {
    setEditionErreur(null);
    setEditionCommande(null);
    setClientEdite(0);
    setLignesEditees([]);
    setDialogOpen(true);
  };

  const ouvrirEdition = (c: Commande) => {
    setEditionErreur(null);
    setEditionCommande(c);
    setClientEdite(c.clientId ?? 0);
    setLignesEditees(
      c.lignes.map((l) => ({
        produitId: l.produitId!,
        reference: l.reference ?? '',
        designation: l.designation ?? '',
        quantite: String(l.quantite),
        prixUnitaire: l.prixUnitaire ?? 0,
        tauxTVA: l.tauxTVA ?? 0,
        remise: l.remise ?? 0,
        depotCode: l.depotCode ?? '',
        existante: true,
      })),
    );
    setDialogOpen(true);
  };

  /**
   * Produits proposables sur une ligne : tout le catalogue sauf ceux déjà pris
   * par une autre ligne, le backend refusant deux fois le même produit.
   */
  const produitsChoisissables = (index: number) =>
    (produitsCatalogue?.content ?? []).filter(
      (p) => !lignesEditees.some((l, i) => i !== index && l.produitId === p.id),
    );

  // Devis d'origine : ses prix priment sur le catalogue, comme cote backend.
  const { data: devisOrigine } = useQuery({
    queryKey: ['devis-detail', editionCommande?.devisId],
    queryFn: () => getDevis(editionCommande!.devisId!),
    enabled: dialogOpen && editionCommande?.devisId != null,
  });

  /** Conditions pre-remplies : celles du devis d'origine, sinon du catalogue. */
  const conditionsDe = (produit: Produit) => {
    const ligneDevis = devisOrigine?.lignes.find((l) => l.produitId === produit.id);
    return ligneDevis
      ? {
          prixUnitaire: ligneDevis.prixUnitaire,
          tauxTVA: ligneDevis.tauxTVA,
          remise: ligneDevis.remise ?? 0,
        }
      : { prixUnitaire: produit.prixUnitaireHT, tauxTVA: produit.tauxTVA, remise: 0 };
  };

  /** Ajoute une ligne vide : le produit se choisit ensuite dans la ligne. */
  const ajouterLigne = () => {
    setLignesEditees((prev) => [
      ...prev,
      {
        produitId: 0,
        reference: '',
        designation: '',
        quantite: '1',
        prixUnitaire: 0,
        tauxTVA: 0,
        remise: 0,
        depotCode: '',
        existante: false,
      },
    ]);
  };

  /** Produit choisi sur une ligne : ses conditions sont reprises telles quelles. */
  const choisirProduit = (index: number, produit: Produit | null) => {
    setLignesEditees((prev) =>
      prev.map((l, i) => {
        if (i !== index) return l;
        if (!produit) {
          return {
            ...l, produitId: 0, reference: '', designation: '',
            prixUnitaire: 0, tauxTVA: 0, remise: 0,
          };
        }
        const { prixUnitaire, tauxTVA, remise } = conditionsDe(produit);
        return {
          ...l,
          produitId: produit.id,
          reference: produit.reference,
          designation: produit.designation,
          prixUnitaire,
          tauxTVA,
          remise,
          // Changer de produit remet la ligne a neuf : son dépôt est a redefinir.
          depotCode: produit.id === l.produitId ? l.depotCode : '',
          existante: produit.id === l.produitId ? l.existante : false,
        };
      }),
    );
  };

  const majQuantite = (index: number, valeur: string) =>
    setLignesEditees((prev) => prev.map((l, i) => (i === index ? { ...l, quantite: valeur } : l)));

  /** Met a jour un montant negocie de la ligne (prix, TVA ou remise). */
  const majLigne = (index: number, champ: 'prixUnitaire' | 'tauxTVA' | 'remise', valeur: number) =>
    setLignesEditees((prev) => prev.map((l, i) => (i === index ? { ...l, [champ]: valeur } : l)));

  const totalHT = lignesEditees.reduce((somme, l) => somme + montantLigne(l), 0);
  const totalTTC = lignesEditees.reduce(
    (somme, l) => somme + montantLigne(l) * (1 + (l.tauxTVA ?? 0) / 100), 0);

  const enregistrerMutation = useMutation({
    mutationFn: () => {
      const payload = {
        clientId: clientEdite,
        lignes: lignesEditees.map((l) => ({
          produitId: l.produitId,
          quantite: Number(l.quantite),
          prixUnitaire: l.prixUnitaire,
          tauxTVA: l.tauxTVA,
          remise: l.remise,
          depotCode: l.depotCode || undefined,
        })),
      };
      return editionCommande
        ? modifierCommande(editionCommande.id, payload)
        : creerCommande(payload);
    },
    onSuccess: () => {
      invaliderTout();
      setDialogOpen(false);
    },
    onError: (e: unknown) =>
      setEditionErreur(
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
          'Enregistrement impossible',
      ),
  });

  const editionValide =
    clientEdite > 0 &&
    lignesEditees.length > 0 &&
    lignesEditees.every((l) => {
      // Une ligne encore vide (produit non choisi) bloque l'enregistrement
      if (!l.produitId) return false;
      const q = Number(l.quantite);
      if (!Number.isFinite(q) || q <= 0) return false;
      // Un produit ajoute sur une commande validée doit preciser son dépôt
      return !(stockDejaSorti && !l.existante && !l.depotCode);
    });

  // --- Bon de livraison ---
  const ouvrirBonLivraison = async (id: number, avecPrix: boolean) => {
    const blob = await telechargerBonLivraison(id, avecPrix);
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  };

  const ouvrirBonPreparation = async (id: number) => {
    const blob = await telechargerBonPreparation(id);
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  };

  const toutesLignesAffectees =
    validationCommande?.lignes.every((l) => depotsLignes[l.id] !== '' && depotsLignes[l.id] !== undefined) ??
    false;

  return (
    <Box>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={{ xs: 1.5, sm: 0 }}
        sx={{ justifyContent: 'space-between', alignItems: { xs: 'stretch', sm: 'center' }, mb: 2 }}
      >
        <EnTetePage titre="Commandes" icone={<IconeRubrique />} />
        {/* Vente au comptoir : une commande peut naitre sans devis */}
        {mesDroits.ecrireCommercial && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={ouvrirCreation}>
            Nouvelle commande
          </Button>
        )}
      </Stack>

      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', rowGap: 2 }}>
          <TextField
            label="Rechercher"
            placeholder="N° commande, client, commercial…"
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
            <MenuItem value="EN_ATTENTE_VALIDATION">Remise à valider</MenuItem>
            <MenuItem value="EN_ATTENTE">En attente</MenuItem>
            <MenuItem value="VALIDEE">Validée</MenuItem>
            <MenuItem value="EN_PREPARATION">En préparation</MenuItem>
            <MenuItem value="LIVREE">Livrée</MenuItem>
            <MenuItem value="ANNULEE">Annulée</MenuItem>
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

      {isError && <Alert severity="error">Erreur de chargement des commandes</Alert>}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Numéro</TableCell>
              <TableCell>Client</TableCell>
              <TableCell>Date</TableCell>
              <TableCell>Devis</TableCell>
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
                <TableCell colSpan={8} align="center">Aucune commande</TableCell>
              </TableRow>
            )}
            {data?.content.map((c) => (
              <TableRow key={c.id} hover sx={{ cursor: 'pointer' }} onClick={() => { setSelectedId(c.id); setDetailOpen(true); }}>
                <TableCell>{c.numero}</TableCell>
                <TableCell>{c.clientNom}</TableCell>
                <TableCell>{new Date(c.dateCommande).toLocaleDateString('fr-FR')}</TableCell>
                <TableCell>{c.devisNumero ?? '-'}</TableCell>
                <TableCell align="right">{formatMontant(c.montantHT)}</TableCell>
                <TableCell align="right">{formatMontant(c.montantTTC)}</TableCell>
                <TableCell>
                  <Chip label={STATUT_LABELS[c.statut]} size="small" color={STATUT_COLORS[c.statut]} />
                </TableCell>
                <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                  {c.statut !== 'LIVREE' && c.statut !== 'ANNULEE' && mesDroits.ecrireCommercial && (
                    <IconButton size="small" color="error" title="Supprimer" onClick={() => deleteMutation.mutate(c.id)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
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

      {/* Dialog detail */}
      <Dialog open={detailOpen} onClose={() => { setDetailOpen(false); setSelectedId(null); }} fullWidth maxWidth="md">
        <DialogTitle>Commande {detailCommande?.numero}</DialogTitle>
        <DialogContent>
          {detailCommande && (
            <Stack spacing={2} sx={{ mt: 1 }}>
              <Stack direction="row" spacing={2}>
                <Typography><strong>Client :</strong> {detailCommande.clientNom}</Typography>
                <Typography><strong>Commercial :</strong> {detailCommande.commercialNom}</Typography>
              </Stack>
              <Stack direction="row" spacing={2}>
                <Typography><strong>Statut :</strong> <Chip label={STATUT_LABELS[detailCommande.statut]} size="small" color={STATUT_COLORS[detailCommande.statut]} /></Typography>
                {detailCommande.devisNumero && <Typography><strong>Devis :</strong> {detailCommande.devisNumero}</Typography>}
              </Stack>
              {/* Le chemin parcouru : c'est de la que se lisent le delai de
                  preparation et le respect du delai promis au client. */}
              <SuiviDates
                titre="Suivi"
                etapes={[
                  { libelle: 'Commande saisie', date: detailCommande.dateCommande },
                  { libelle: 'Remise validée', date: detailCommande.dateValidationRemise },
                  { libelle: 'Validée, stock reserve', date: detailCommande.dateValidation },
                  { libelle: 'Mise en préparation', date: detailCommande.dateEnPreparation },
                  { libelle: 'Livrée', date: detailCommande.dateLivraison },
                  { libelle: 'Annulée', date: detailCommande.dateAnnulation },
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
                      <TableCell>Dépôt</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {detailCommande.lignes.map((l) => (
                      <TableRow key={l.id}>
                        <TableCell>{l.reference}</TableCell>
                        <TableCell>{l.designation}</TableCell>
                        <TableCell align="right">{l.quantite}</TableCell>
                        <TableCell align="right">{formatMontant(l.prixUnitaire)}</TableCell>
                        <TableCell align="right">{l.tauxTVA} %</TableCell>
                        <TableCell align="right">{l.remise ?? 0} %</TableCell>
                        <TableCell align="right">{formatMontant(l.montantLigne)}</TableCell>
                        <TableCell>{l.depotCode ? `Dépôt ${l.depotCode}` : '-'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <Stack direction="row" spacing={4} sx={{ justifyContent: 'flex-end' }}>
                <Typography><strong>HT :</strong> {formatMontant(detailCommande.montantHT)}</Typography>
                <Typography><strong>TTC :</strong> {formatMontant(detailCommande.montantTTC)}</Typography>
              </Stack>
              {/* Remise au-dela du seuil : la commande est gelee jusqu'a l'aval de
                  l'encadrement. Seules l'annulation et la baisse de la remise restent. */}
              {detailCommande.statut === 'EN_ATTENTE_VALIDATION' && (
                <Alert severity="warning">
                  Une remise depasse le seuil autorise : la commande attend la validation du
                  responsable commercial. Aucun document ne peut en sortir d'ici la.
                </Alert>
              )}
              {/* Une commande EN_ATTENTE se valide via le choix des depots (prise de
                  stock) : geste d'entrepot. L'annulation, elle, reste commerciale. */}
              {(detailCommande.statut === 'EN_ATTENTE'
                || detailCommande.statut === 'EN_ATTENTE_VALIDATION') && (
                <Stack direction="row" spacing={1}>
                  {detailCommande.statut === 'EN_ATTENTE' && mesDroits.traiterCommande && (
                    <Button
                      variant="contained"
                      color="success"
                      startIcon={<CheckCircleIcon />}
                      onClick={() => ouvrirValidation(detailCommande)}
                    >
                      Valider (choix des depots)
                    </Button>
                  )}
                  {mesDroits.annulerCommande && (
                    <Button
                      variant="outlined"
                      color="error"
                      onClick={() => statutMutation.mutate({ id: detailCommande.id, statut: 'ANNULEE' })}
                      disabled={statutMutation.isPending}
                    >
                      Annuler la commande
                    </Button>
                  )}
                </Stack>
              )}

              {/* Une fois validee : suite du cycle logistique, reservee au magasinier */}
              {(detailCommande.statut === 'VALIDEE' || detailCommande.statut === 'EN_PREPARATION') && (
                <Stack direction="row" spacing={1}>
                  <Typography sx={{ alignSelf: 'center' }}><strong>Faire avancer :</strong></Typography>
                  {(['EN_PREPARATION', 'LIVREE', 'ANNULEE'] as StatutCommande[])
                    .filter((s) => (s === 'ANNULEE' ? mesDroits.annulerCommande : mesDroits.traiterCommande))
                    .map((s) => (
                      <Button
                        key={s}
                        size="small"
                        color={s === 'ANNULEE' ? 'error' : 'primary'}
                        variant={detailCommande.statut === s ? 'contained' : 'outlined'}
                        onClick={() => statutMutation.mutate({ id: detailCommande.id, statut: s })}
                        disabled={statutMutation.isPending || detailCommande.statut === s}
                      >
                        {STATUT_LABELS[s]}
                      </Button>
                    ))}
                </Stack>
              )}
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setDetailOpen(false); setSelectedId(null); }}>Fermer</Button>
          {/* Les lignes se retouchent tant que la commande n'est ni livree ni annulee */}
          {detailCommande && detailCommande.statut !== 'LIVREE' && detailCommande.statut !== 'ANNULEE'
            && mesDroits.ecrireCommercial && (
            <Button startIcon={<EditIcon />} onClick={() => ouvrirEdition(detailCommande)}>
              Modifier les lignes
            </Button>
          )}
          {/* Liste de picking pour preparer physiquement la commande */}
          {detailCommande?.statut === 'EN_PREPARATION' && (
            <Button
              startIcon={<ChecklistIcon />}
              onClick={() => ouvrirBonPreparation(detailCommande.id)}
            >
              Bon de preparation
            </Button>
          )}
          {/* Remise non tranchee : aucun document ne sort, le prix n'est pas arrete */}
          {detailCommande && detailCommande.statut !== 'EN_ATTENTE_VALIDATION' && (
            <>
              <Button
                startIcon={<LocalShippingIcon />}
                onClick={() => ouvrirBonLivraison(detailCommande.id, false)}
              >
                BL sans prix
              </Button>
              <Button
                variant="contained"
                startIcon={<LocalShippingIcon />}
                onClick={() => ouvrirBonLivraison(detailCommande.id, true)}
              >
                BL avec prix
              </Button>
            </>
          )}
          {detailCommande?.statut === 'EN_ATTENTE_VALIDATION' && mesDroits.encadrer && (
            <Button
              variant="contained"
              color="success"
              startIcon={<GavelIcon />}
              onClick={() => validerRemiseMutation.mutate(detailCommande.id)}
              disabled={validerRemiseMutation.isPending}
            >
              Valider la remise
            </Button>
          )}
        </DialogActions>
      </Dialog>

      {/* Dialog de saisie : creation directe ou correction d'une commande */}
      <Dialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        fullWidth
        maxWidth="md"
      >
        <DialogTitle>
          {editionCommande ? `Modifier ${editionCommande.numero}` : 'Nouvelle commande'}
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Ajustez les quantites, retirez des lignes ou ajoutez n'importe quel produit du catalogue.
            {stockDejaSorti
              ? ' Cette commande est validée : les écarts ajusteront les quantités réservées.'
              : ' Tant que la commande n\'est pas validee, le stock n\'est pas engage.'}
          </Typography>

          <Autocomplete
            options={clientsData?.content ?? []}
            value={(clientsData?.content ?? []).find((c) => c.id === clientEdite) ?? null}
            getOptionLabel={(c) => (c.prenom ? `${c.prenom} ${c.nom}` : c.nom)}
            disabled={clientFige}
            onChange={(_, client) => setClientEdite(client?.id ?? 0)}
            renderInput={(params) => (
              <TextField
                {...params}
                size="small"
                label="Client"
                required
                helperText={clientFige
                  ? 'Fige : commande issue d\'un devis ou déjà validée'
                  : undefined}
              />
            )}
            sx={{ mb: 2, maxWidth: 480 }}
          />

          {/* Meme geste que sur le devis : on ajoute une ligne, puis on y choisit
              le produit. Prix et TVA sont imposes par le devis d'origine ou le
              catalogue : ils s'affichent sans etre saisissables. */}
          <Typography variant="subtitle1" sx={{ mb: 1 }}>Lignes de la commande</Typography>
          <Stack spacing={2}>
            {lignesEditees.map((l, index) => (
              <Paper key={index} variant="outlined" sx={{ p: 2 }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
                  <Autocomplete
                    size="small"
                    sx={{ minWidth: 300, flex: 1 }}
                    options={produitsChoisissables(index)}
                    value={(produitsCatalogue?.content ?? []).find((p) => p.id === l.produitId) ?? null}
                    onChange={(_, produit) => choisirProduit(index, produit)}
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
                    onChange={(e) => majQuantite(index, e.target.value)}
                    slotProps={{ htmlInput: { step: 'any', min: 0 } }}
                    sx={{ width: 90 }}
                  />
                  <TextField
                    label="Prix unit."
                    type="number"
                    size="small"
                    disabled={mesDroits.prixImposes}
                    value={l.prixUnitaire}
                    onChange={(e) => majLigne(index, 'prixUnitaire', parseFloat(e.target.value) || 0)}
                    sx={{ width: 120 }}
                  />
                  <TextField
                    label="TVA %"
                    type="number"
                    size="small"
                    disabled={mesDroits.prixImposes}
                    value={l.tauxTVA}
                    onChange={(e) => majLigne(index, 'tauxTVA', parseFloat(e.target.value) || 0)}
                    sx={{ width: 80 }}
                  />
                  <TextField
                    label="Remise %"
                    type="number"
                    size="small"
                    value={l.remise}
                    onChange={(e) => majLigne(index, 'remise', parseFloat(e.target.value) || 0)}
                    sx={{ width: 80 }}
                  />
                  {/* Un produit ajoute sur une commande validee doit dire d'ou il sort */}
                  {stockDejaSorti && (
                    l.existante ? (
                      <Typography variant="body2" sx={{ minWidth: 90 }}>
                        {l.depotCode ? `Dépôt ${l.depotCode}` : '-'}
                      </Typography>
                    ) : (
                      <TextField
                        select
                        label="Dépôt"
                        size="small"
                        value={l.depotCode}
                        onChange={(e) =>
                          setLignesEditees((prev) =>
                            prev.map((x, i) => (i === index ? { ...x, depotCode: e.target.value } : x)),
                          )
                        }
                        sx={{ minWidth: 120 }}
                      >
                        {(depots ?? []).map((d) => (
                          <MenuItem key={d.id} value={d.code}>Depot {d.code}</MenuItem>
                        ))}
                      </TextField>
                    )
                  )}
                  <IconButton
                    color="error"
                    size="small"
                    title="Retirer la ligne"
                    onClick={() => setLignesEditees((prev) => prev.filter((_, i) => i !== index))}
                  >
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Stack>
              </Paper>
            ))}
          </Stack>
          <Button startIcon={<AddIcon />} onClick={ajouterLigne} sx={{ mt: 1 }}>
            Ajouter une ligne
          </Button>

          {/* Totaux en direct : on voit ce que vaut la commande avant d'enregistrer */}
          {lignesEditees.length > 0 && (
            <Stack direction="row" spacing={4} sx={{ justifyContent: 'flex-end', mt: 2 }}>
              <Typography><strong>Total HT :</strong> {formatMontant(totalHT)}</Typography>
              <Typography><strong>Total TTC :</strong> {formatMontant(totalTTC)}</Typography>
            </Stack>
          )}

          {editionErreur && <Alert severity="error" sx={{ mt: 2 }}>{editionErreur}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Annuler</Button>
          <Button
            variant="contained"
            disabled={!editionValide || enregistrerMutation.isPending}
            onClick={() => enregistrerMutation.mutate()}
          >
            {editionCommande ? 'Enregistrer' : 'Créer'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Dialog de validation : choix du depot de prelevement par ligne */}
      <Dialog
        open={validationCommande !== null}
        onClose={() => setValidationCommande(null)}
        fullWidth
        maxWidth="md"
      >
        <DialogTitle>Valider la commande {validationCommande?.numero}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Choisissez le depot d'ou prelever chaque produit. Les quantites y seront
            reservees ; le stock ne sortira reellement qu'au passage a « Livree ».
          </Typography>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Produit</TableCell>
                <TableCell align="right">Qté</TableCell>
                <TableCell>Dépôt</TableCell>
                <TableCell align="right">Dispo</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {validationCommande?.lignes.map((l) => {
                const depotChoisi = depotsLignes[l.id];
                const dispo =
                  depotChoisi
                    ? stockParProduit[l.produitId]?.[depotChoisi] ?? 0
                    : undefined;
                const insuffisant = dispo !== undefined && dispo < l.quantite;
                return (
                  <TableRow key={l.id}>
                    <TableCell>{l.designation}</TableCell>
                    <TableCell align="right">{l.quantite}</TableCell>
                    <TableCell>
                      <TextField
                        select
                        size="small"
                        value={depotChoisi ?? ''}
                        onChange={(e) =>
                          setDepotsLignes((prev) => ({ ...prev, [l.id]: e.target.value }))
                        }
                        sx={{ minWidth: 140 }}
                      >
                        {(depots ?? []).map((d) => {
                          const q = stockParProduit[l.produitId]?.[d.code] ?? 0;
                          return (
                            <MenuItem key={d.id} value={d.code}>
                              Depot {d.code} ({q})
                            </MenuItem>
                          );
                        })}
                      </TextField>
                    </TableCell>
                    <TableCell align="right">
                      {dispo === undefined ? (
                        '-'
                      ) : (
                        <Chip
                          label={dispo}
                          size="small"
                          color={insuffisant ? 'error' : 'success'}
                        />
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
          {validationErreur && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {validationErreur}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setValidationCommande(null)}>Annuler</Button>
          <Button
            variant="contained"
            color="success"
            disabled={!toutesLignesAffectees || validerMutation.isPending}
            onClick={() => validationCommande && validerMutation.mutate(validationCommande)}
          >
            Confirmer la validation
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
