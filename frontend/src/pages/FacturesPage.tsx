import { useState } from 'react';
import { libelle, MODE_PAIEMENT } from '../utils/libelles';
import { useAuth } from '../auth/AuthContext';
import { droits } from '../auth/droits';
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
import DeleteIcon from '@mui/icons-material/Delete';
import PaymentIcon from '@mui/icons-material/Payment';
import EditIcon from '@mui/icons-material/Edit';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import EmailIcon from '@mui/icons-material/Email';
import {
  creerFacture,
  envoyerFactureParEmail,
  listerFactures,
  modifierFacture,
  supprimerFacture,
  getFacture,
  telechargerFacturePdf,
  type FactureQuery,
} from '../api/factures';
import { listerCommandes } from '../api/commandes';
import {
  creerPaiement,
  deposerEffet,
  encaisserEffet,
  listerPaiements,
  rejeterEffet,
  supprimerPaiement,
} from '../api/paiements';
import type { ModePaiement, StatutFacture } from '../api/types';
import { formatMontant } from '../utils/format';
import SuiviDates from '../components/SuiviDates';

const STATUT_COLORS: Record<StatutFacture, 'default' | 'warning' | 'success' | 'error' | 'info'> = {
  EMISE: 'info',
  PARTIELLEMENT_PAYEE: 'warning',
  PAYEE: 'success',
  EN_RETARD: 'error',
  ANNULEE: 'error',
};

const STATUT_LABELS: Record<StatutFacture, string> = {
  EMISE: 'Émise',
  PARTIELLEMENT_PAYEE: 'Part. payée',
  PAYEE: 'Payée',
  EN_RETARD: 'En retard',
  ANNULEE: 'Annulée',
};

const messageErreur = (e: unknown, defaut: string) =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? defaut;

/** Un effet n'a pas encore livre son argent tant qu'il n'est pas encaisse. */
const STATUT_PAIEMENT: Record<string, { libelle: string; couleur: 'default' | 'info' | 'success' | 'error' }> = {
  RECU: { libelle: 'Reçu', couleur: 'default' },
  DEPOSE: { libelle: 'Rémis en banque', couleur: 'info' },
  ENCAISSE: { libelle: 'Encaissé', couleur: 'success' },
  REJETE: { libelle: 'Rejeté', couleur: 'error' },
};

export default function FacturesPage() {
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
  const [createOpen, setCreateOpen] = useState(false);
  const [createCommandeId, setCreateCommandeId] = useState<number>(0);
  const [createEcheance, setCreateEcheance] = useState('');
  const [formError, setFormError] = useState<string | null>(null);

  // Paiement dialog
  const [paiementOpen, setPaiementOpen] = useState(false);
  const [paiementMontant, setPaiementMontant] = useState<number>(0);
  const [paiementMode, setPaiementMode] = useState<ModePaiement>('VIREMENT');
  const [paiementRef, setPaiementRef] = useState('');
  // Champs d'effet : ne concernent que le chèque et la traite
  const [effetNumero, setEffetNumero] = useState('');
  const [effetBanque, setEffetBanque] = useState('');
  const [effetEmission, setEffetEmission] = useState('');
  const [effetReception, setEffetReception] = useState('');
  const [effetEcheance, setEffetEcheance] = useState('');
  const [paiementErreur, setPaiementErreur] = useState<string | null>(null);
  // Rejet d'un effet : le motif est obligatoire
  const [rejetId, setRejetId] = useState<number | null>(null);
  const [rejetMotif, setRejetMotif] = useState('');
  const [effetErreur, setEffetErreur] = useState<string | null>(null);

  const query: FactureQuery = {
    page,
    size,
    sort: 'dateFacture,desc',
    recherche: recherche || undefined,
    statut: (statutFilter as StatutFacture) || undefined,
    dateMin: dateMin || undefined,
    dateMax: dateMax || undefined,
  };

  const { data, isLoading, isError } = useQuery({
    queryKey: ['factures', query],
    queryFn: () => listerFactures(query),
  });

  const { data: detailFacture } = useQuery({
    queryKey: ['facture-detail', selectedId],
    queryFn: () => getFacture(selectedId!),
    enabled: selectedId !== null,
  });

  const { data: paiements } = useQuery({
    queryKey: ['paiements', selectedId],
    queryFn: () => listerPaiements(selectedId!),
    enabled: selectedId !== null,
  });


  // Seules les commandes pas encore facturées : une commande ne se facture
  // qu'une fois, autant ne pas les proposer.
  const { data: commandesData } = useQuery({
    queryKey: ['commandes-facturables'],
    queryFn: () => listerCommandes({ size: 200, nonFacturee: true }),
  });

  const createMutation = useMutation({
    mutationFn: creerFacture,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['factures'] });
      queryClient.invalidateQueries({ queryKey: ['commandes-facturables'] });
      setCreateOpen(false);
      setCreateCommandeId(0);
      setCreateEcheance('');
      setFormError(null);
    },
    // Ex. commande déjà facturée : le backend nomme la facturé en cause.
    onError: (e: unknown) =>
      setFormError(
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
          'Erreur lors de la création de la facture',
      ),
  });

  const deleteMutation = useMutation({
    mutationFn: supprimerFacture,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['factures'] });
      // La commande redevient facturable.
      queryClient.invalidateQueries({ queryKey: ['commandes-facturables'] });
    },
    // Ex. facturé déjà réglée en partie : on remonte le message du backend.
    onError: (e: unknown) =>
      setSuppressionErreur(
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
          'Suppression impossible',
      ),
  });

  // --- Renegociation de l'échéance ---
  const [echeanceOpen, setEcheanceOpen] = useState(false);
  const [echeanceEditee, setEcheanceEditee] = useState('');
  const [echeanceErreur, setEcheanceErreur] = useState<string | null>(null);
  const [suppressionErreur, setSuppressionErreur] = useState<string | null>(null);

  // --- PDF et envoi au client ---
  const [emailSuccess, setEmailSuccess] = useState<string | null>(null);

  const ouvrirPdf = async (id: number) => {
    const blob = await telechargerFacturePdf(id);
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  };

  const emailMutation = useMutation({
    mutationFn: (id: number) => envoyerFactureParEmail(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['factures'] });
      queryClient.invalidateQueries({ queryKey: ['facture-detail'] });
      setEmailErreur(null);
      setEmailSuccess('Facture envoyée au client par email');
    },
    onError: (e: unknown) =>
      setEmailErreur(
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
          "Envoi de l'email impossible",
      ),
  });
  const [emailErreur, setEmailErreur] = useState<string | null>(null);

  const echeanceMutation = useMutation({
    mutationFn: () => modifierFacture(selectedId!, echeanceEditee),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['factures'] });
      queryClient.invalidateQueries({ queryKey: ['facture-detail'] });
      setEcheanceOpen(false);
    },
    onError: (e: unknown) =>
      setEcheanceErreur(
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
          'Modification impossible',
      ),
  });

  const paiementMutation = useMutation({
    mutationFn: creerPaiement,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['facture-detail'] });
      queryClient.invalidateQueries({ queryKey: ['paiements'] });
      queryClient.invalidateQueries({ queryKey: ['factures'] });
      setPaiementOpen(false);
      setPaiementMontant(0);
      setPaiementRef('');
      setEffetNumero('');
      setEffetBanque('');
      setEffetEmission('');
      setEffetReception('');
      setEffetEcheance('');
    },
    onError: (e: unknown) =>
      setPaiementErreur(messageErreur(e, 'Enregistrement du paiement impossible')),
  });

  /**
   * Cycle d'un effet. Les trois étapes partagent le même rafraichissement :
   * chacune peut changer le montant paye, donc le statut de la facture.
   */
  const suiteEffet = {
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['facture-detail'] });
      queryClient.invalidateQueries({ queryKey: ['paiements'] });
      queryClient.invalidateQueries({ queryKey: ['factures'] });
      queryClient.invalidateQueries({ queryKey: ['tableau-de-bord'] });
      setEffetErreur(null);
    },
    onError: (e: unknown) => setEffetErreur(messageErreur(e, 'Opération impossible')),
  };

  const supprimerPaiementMutation = useMutation({
    mutationFn: supprimerPaiement,
    ...suiteEffet,
  });
  const deposerMutation = useMutation({ mutationFn: (id: number) => deposerEffet(id), ...suiteEffet });
  const encaisserMutation = useMutation({ mutationFn: (id: number) => encaisserEffet(id), ...suiteEffet });
  const rejeterMutation = useMutation({
    mutationFn: ({ id, motif }: { id: number; motif: string }) => rejeterEffet(id, motif),
    ...suiteEffet,
    onSuccess: () => {
      suiteEffet.onSuccess();
      setRejetId(null);
      setRejetMotif('');
    },
  });

  const handleCreate = () => {
    setFormError(null);
    if (!createCommandeId || !createEcheance) {
      setFormError('Veuillez remplir tous les champs');
      return;
    }
    createMutation.mutate({ commandeId: createCommandeId, dateEcheance: createEcheance });
  };

  const handlePaiement = () => {
    if (!detailFacture || paiementMontant <= 0) return;
    const estUnEffet = paiementMode === 'CHEQUE' || paiementMode === 'TRAITE';
    paiementMutation.mutate({
      factureId: detailFacture.id,
      montant: paiementMontant,
      modePaiement: paiementMode,
      reference: paiementRef || undefined,
      numeroEffet: estUnEffet ? effetNumero || undefined : undefined,
      banqueEmettrice: estUnEffet ? effetBanque || undefined : undefined,
      dateEmission: estUnEffet ? effetEmission || undefined : undefined,
      dateReception: estUnEffet ? effetReception || undefined : undefined,
      dateEcheance: estUnEffet ? effetEcheance || undefined : undefined,
    });
  };

  const commandes = commandesData?.content ?? [];

  return (
    <Box>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h4">Factures</Typography>
        {mesDroits.ecrireFacture && (
          <Button variant="contained" startIcon={<PaymentIcon />} onClick={() => setCreateOpen(true)}>
            Nouvelle facture
          </Button>
        )}
      </Stack>

      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', rowGap: 2 }}>
          <TextField
            label="Rechercher"
            placeholder="N° facture, client…"
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
            <MenuItem value="EMISE">Émise</MenuItem>
            <MenuItem value="PARTIELLEMENT_PAYEE">Partiellement payée</MenuItem>
            <MenuItem value="PAYEE">Payée</MenuItem>
            <MenuItem value="EN_RETARD">En retard</MenuItem>
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

      {isError && <Alert severity="error">Erreur de chargement des factures</Alert>}
      {suppressionErreur && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setSuppressionErreur(null)}>
          {suppressionErreur}
        </Alert>
      )}
      {emailSuccess && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setEmailSuccess(null)}>
          {emailSuccess}
        </Alert>
      )}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Numéro</TableCell>
              <TableCell>Client</TableCell>
              <TableCell>Date</TableCell>
              <TableCell>Échéance</TableCell>
              <TableCell align="right">Montant TTC</TableCell>
              <TableCell align="right">Paye</TableCell>
              <TableCell align="right">Reste</TableCell>
              <TableCell>Statut</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && (
              <TableRow>
                <TableCell colSpan={9} align="center">Chargement...</TableCell>
              </TableRow>
            )}
            {data?.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={9} align="center">Aucune facture</TableCell>
              </TableRow>
            )}
            {data?.content.map((f) => (
              <TableRow key={f.id} hover sx={{ cursor: 'pointer' }} onClick={() => { setSelectedId(f.id); setDetailOpen(true); }}>
                <TableCell>{f.numero}</TableCell>
                <TableCell>{f.clientNom}</TableCell>
                <TableCell>{new Date(f.dateFacture).toLocaleDateString('fr-FR')}</TableCell>
                <TableCell>{new Date(f.dateEcheance).toLocaleDateString('fr-FR')}</TableCell>
                <TableCell align="right">{formatMontant(f.montantTTC)}</TableCell>
                <TableCell align="right">{formatMontant(f.montantPaye)}</TableCell>
                <TableCell align="right">{formatMontant(f.resteAPayer)}</TableCell>
                <TableCell>
                  <Chip label={STATUT_LABELS[f.statut]} size="small" color={STATUT_COLORS[f.statut]} />
                </TableCell>
                <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                  {f.statut === 'EMISE' && mesDroits.ecrireFacture && (
                    <IconButton size="small" color="error" title="Supprimer" onClick={() => deleteMutation.mutate(f.id)}>
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

      {/* Dialog creation facture */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Nouvelle facture</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Commande"
              select
              value={createCommandeId}
              onChange={(e) => setCreateCommandeId(Number(e.target.value))}
              required
            >
              {/* Une commande dont la remise n'est pas tranchee n'a pas de prix ferme */}
              {commandes
                .filter((c) => c.statut !== 'ANNULEE' && c.statut !== 'EN_ATTENTE_VALIDATION')
                .map((c) => (
                  <MenuItem key={c.id} value={c.id}>
                    {c.numero} - {c.clientNom} ({formatMontant(c.montantTTC)})
                  </MenuItem>
                ))}
            </TextField>
            <TextField
              label="Date d'échéance"
              type="date"
              value={createEcheance}
              onChange={(e) => setCreateEcheance(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
              required
            />
            {formError && <Alert severity="error">{formError}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setCreateOpen(false); setFormError(null); }}>Annuler</Button>
          <Button variant="contained" onClick={handleCreate} disabled={createMutation.isPending}>
            Creer
          </Button>
        </DialogActions>
      </Dialog>

      {/* Dialog detail facture */}
      <Dialog open={detailOpen} onClose={() => { setDetailOpen(false); setSelectedId(null); }} fullWidth maxWidth="md">
        <DialogTitle>Facture {detailFacture?.numero}</DialogTitle>
        <DialogContent>
          {detailFacture && (
            <Stack spacing={2} sx={{ mt: 1 }}>
              <Stack direction="row" spacing={2}>
                <Typography><strong>Client :</strong> {detailFacture.clientNom}</Typography>
                <Typography><strong>Statut :</strong> <Chip label={STATUT_LABELS[detailFacture.statut]} size="small" color={STATUT_COLORS[detailFacture.statut]} /></Typography>
              </Stack>
              {detailFacture.commandeNumero && (
                <Typography><strong>Commande :</strong> {detailFacture.commandeNumero}</Typography>
              )}
              {/* Emission, transmission, reglement : la meme lecture que sur les
                  autres documents, plutot que des dates eparpillees dans la fiche. */}
              <SuiviDates
                titre="Suivi"
                etapes={[
                  { libelle: 'Facture émise', date: detailFacture.dateFacture },
                  { libelle: 'Transmise au client', date: detailFacture.dateEnvoiEmail },
                  { libelle: 'Soldée', date: detailFacture.dateReglement },
                  {
                    libelle: detailFacture.statut === 'EN_RETARD'
                      ? 'Échéance dépassée' : 'Échéance',
                    date: detailFacture.dateEcheance,
                    prevu: detailFacture.statut !== 'EN_RETARD',
                  },
                ]}
              />
              {emailErreur && <Alert severity="error" onClose={() => setEmailErreur(null)}>{emailErreur}</Alert>}
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
                    {detailFacture.lignes.map((l) => (
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
                <Typography><strong>HT :</strong> {formatMontant(detailFacture.montantHT)}</Typography>
                <Typography><strong>TTC :</strong> {formatMontant(detailFacture.montantTTC)}</Typography>
                <Typography><strong>Paye :</strong> {formatMontant(detailFacture.montantPaye)}</Typography>
                <Typography color="error"><strong>Reste :</strong> {formatMontant(detailFacture.resteAPayer)}</Typography>
              </Stack>

              {paiements && paiements.length > 0 && (
                <>
                  <Typography variant="subtitle1">Paiements</Typography>
                  {effetErreur && <Alert severity="error" onClose={() => setEffetErreur(null)}>{effetErreur}</Alert>}
                  <TableContainer>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Enregistré le</TableCell>
                          <TableCell>Mode</TableCell>
                          <TableCell>Référence</TableCell>
                          <TableCell>Cycle de l'effet</TableCell>
                          <TableCell>État</TableCell>
                          <TableCell align="right">Montant</TableCell>
                          <TableCell align="right">Actions</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {paiements.map((p) => (
                          <TableRow key={p.id}>
                            <TableCell>{new Date(p.datePaiement).toLocaleDateString('fr-FR')}</TableCell>
                            <TableCell>{libelle(MODE_PAIEMENT, p.modePaiement)}</TableCell>
                            <TableCell>
                              {p.numeroEffet ?? p.reference ?? '-'}
                              {p.banqueEmettrice && (
                                <Typography variant="caption" color="text.secondary"
                                  sx={{ display: 'block' }}>
                                  {p.banqueEmettrice}
                                </Typography>
                              )}
                            </TableCell>
                            {/* Le cycle a sa propre colonne : melange a la
                                reference, on ne savait plus ce qu'on lisait. */}
                            <TableCell>
                              {p.estUnEffet ? (
                                <SuiviDates
                                  dense
                                  etapes={[
                                    { libelle: 'Émis', date: p.dateEmission },
                                    { libelle: 'Reçu', date: p.dateReception },
                                    { libelle: 'Rémis en banque', date: p.dateRemise },
                                    { libelle: 'Encaissé', date: p.dateEncaissement },
                                    { libelle: 'Échéance', date: p.dateEcheance,
                                      prevu: !p.dateEncaissement },
                                  ]}
                                />
                              ) : (
                                <Typography variant="caption" color="text.secondary">
                                  Encaisse a la remise
                                </Typography>
                              )}
                            </TableCell>
                            <TableCell>
                              <Chip
                                size="small"
                                label={STATUT_PAIEMENT[p.statut]?.libelle ?? p.statut}
                                color={STATUT_PAIEMENT[p.statut]?.couleur ?? 'default'}
                              />
                              {p.motifRejet && (
                                <Typography variant="caption" color="error" sx={{ display: 'block' }}>
                                  {p.motifRejet}
                                </Typography>
                              )}
                            </TableCell>
                            <TableCell align="right">{formatMontant(p.montant)}</TableCell>
                            <TableCell align="right">
                              {/* Seuls les effets ont un cycle ; le reste est deja encaisse */}
                              {p.estUnEffet && mesDroits.ecrireFacture && (
                                <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                                  {p.statut === 'RECU' && (
                                    <Button size="small" onClick={() => deposerMutation.mutate(p.id)}>
                                      Remettre en banque
                                    </Button>
                                  )}
                                  {(p.statut === 'RECU' || p.statut === 'DEPOSE') && (
                                    <>
                                      <Button size="small" variant="contained" color="success"
                                        onClick={() => encaisserMutation.mutate(p.id)}>
                                        Encaisser
                                      </Button>
                                      <Button size="small" color="error"
                                        onClick={() => { setRejetId(p.id); setRejetMotif(''); }}>
                                        Rejeter
                                      </Button>
                                    </>
                                  )}
                                  {p.statut === 'ENCAISSE' && (
                                    <Button size="small" color="error"
                                      onClick={() => { setRejetId(p.id); setRejetMotif(''); }}>
                                      Rejeter
                                    </Button>
                                  )}
                                  {/* Un effet rejete garde sa ligne et bloque la
                                      suppression de la facture : il faut pouvoir l'oter */}
                                  {p.statut !== 'ENCAISSE' && (
                                    <IconButton size="small" color="error" title="Supprimer ce paiement"
                                      onClick={() => supprimerPaiementMutation.mutate(p.id)}>
                                      <DeleteIcon fontSize="small" />
                                    </IconButton>
                                  )}
                                </Stack>
                              )}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </>
              )}
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setDetailOpen(false); setSelectedId(null); }}>Fermer</Button>
          {detailFacture && (
            <>
              <Button startIcon={<PictureAsPdfIcon />} onClick={() => ouvrirPdf(detailFacture.id)}>
                PDF
              </Button>
              {/* Un renvoi est possible : la date d'envoi est simplement mise a jour */}
              {mesDroits.ecrireFacture && (
              <Button
                startIcon={<EmailIcon />}
                disabled={emailMutation.isPending}
                onClick={() => emailMutation.mutate(detailFacture.id)}
              >
                {detailFacture.dateEnvoiEmail ? "Renvoyer l'email" : 'Envoyer au client'}
              </Button>
              )}
            </>
          )}
          {/* Seule l'echeance se renegocie : montants et lignes viennent de la commande */}
          {detailFacture && mesDroits.ecrireFacture && (
            <Button
              startIcon={<EditIcon />}
              onClick={() => {
                setEcheanceEditee(detailFacture.dateEcheance);
                setEcheanceErreur(null);
                setEcheanceOpen(true);
              }}
            >
              Modifier l'echeance
            </Button>
          )}
          {detailFacture && detailFacture.resteAPayer > 0 && detailFacture.statut !== 'ANNULEE'
            && mesDroits.ecrireFacture && (
            <Button
              variant="contained"
              startIcon={<PaymentIcon />}
              onClick={() => {
                setPaiementMontant(detailFacture.resteAPayer);
                setPaiementOpen(true);
              }}
            >
              Enregistrer paiement
            </Button>
          )}
        </DialogActions>
      </Dialog>

      {/* Dialog de modification de l'echeance */}
      <Dialog open={echeanceOpen} onClose={() => setEcheanceOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Echeance de {detailFacture?.numero}</DialogTitle>
        <DialogContent>
          <TextField
            label="Date d'échéance"
            type="date"
            fullWidth
            value={echeanceEditee}
            onChange={(e) => setEcheanceEditee(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
            sx={{ mt: 1 }}
          />
          {echeanceErreur && <Alert severity="error" sx={{ mt: 2 }}>{echeanceErreur}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEcheanceOpen(false)}>Annuler</Button>
          <Button
            variant="contained"
            disabled={!echeanceEditee || echeanceMutation.isPending}
            onClick={() => echeanceMutation.mutate()}
          >
            Enregistrer
          </Button>
        </DialogActions>
      </Dialog>

      {/* Dialog paiement */}
      <Dialog open={paiementOpen} onClose={() => setPaiementOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Enregistrer un paiement</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Montant"
              type="number"
              value={paiementMontant}
              onChange={(e) => setPaiementMontant(parseFloat(e.target.value) || 0)}
              required
            />
            <TextField
              label="Mode de paiement"
              select
              value={paiementMode}
              onChange={(e) => setPaiementMode(e.target.value as ModePaiement)}
            >
              <MenuItem value="VIREMENT">Virement</MenuItem>
              <MenuItem value="CHEQUE">Chèque</MenuItem>
              <MenuItem value="TRAITE">Traite</MenuItem>
              <MenuItem value="CARTE">Carte</MenuItem>
              <MenuItem value="ESPECES">Espèces</MenuItem>
            </TextField>
            <TextField
              label="Référence"
              value={paiementRef}
              onChange={(e) => setPaiementRef(e.target.value)}
            />

            {/* Un effet n'est pas encaisse a la saisie : il entre au portefeuille */}
            {(paiementMode === 'CHEQUE' || paiementMode === 'TRAITE') && (
              <>
                <Alert severity="info">
                  Cet effet sera enregistre comme <strong>reçu</strong> : il ne soldera la
                  facture qu'une fois encaisse.
                </Alert>
                <TextField
                  label={paiementMode === 'CHEQUE' ? 'Numéro du chèque' : 'Numéro de la traite'}
                  value={effetNumero}
                  onChange={(e) => setEffetNumero(e.target.value)}
                />
                <TextField
                  label="Banque émettrice"
                  value={effetBanque}
                  onChange={(e) => setEffetBanque(e.target.value)}
                />
                <Stack direction="row" spacing={2}>
                  <TextField
                    label="Date d'émission"
                    type="date"
                    value={effetEmission}
                    onChange={(e) => setEffetEmission(e.target.value)}
                    slotProps={{ inputLabel: { shrink: true } }}
                    helperText="Portée sur l'effet par son émetteur"
                    fullWidth
                  />
                  <TextField
                    label="Date de réception"
                    type="date"
                    value={effetReception}
                    onChange={(e) => setEffetReception(e.target.value)}
                    slotProps={{ inputLabel: { shrink: true } }}
                    helperText="Vide : la date du jour"
                    fullWidth
                  />
                </Stack>
                <TextField
                  label="Échéance"
                  type="date"
                  value={effetEcheance}
                  onChange={(e) => setEffetEcheance(e.target.value)}
                  slotProps={{ inputLabel: { shrink: true } }}
                  helperText="Date à laquelle l'effet devient payable"
                />
              </>
            )}
            {paiementErreur && <Alert severity="error">{paiementErreur}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPaiementOpen(false)}>Annuler</Button>
          <Button variant="contained" onClick={handlePaiement} disabled={paiementMutation.isPending}>
            Valider
          </Button>
        </DialogActions>
      </Dialog>

      {/* Rejet d'un effet : sans motif, on ne sait pas quoi dire au client */}
      <Dialog open={rejetId !== null} onClose={() => setRejetId(null)} fullWidth maxWidth="sm">
        <DialogTitle>Rejeter cet effet</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Alert severity="warning">
              Le montant retombera : la facture redeviendra due, et le client pourra se
              retrouver au-dessus de son plafond de credit.
            </Alert>
            <TextField
              label="Motif du rejet"
              value={rejetMotif}
              onChange={(e) => setRejetMotif(e.target.value)}
              placeholder="Provision insuffisante, compte clôturé, opposition..."
              required
              autoFocus
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejetId(null)}>Annuler</Button>
          <Button
            variant="contained"
            color="error"
            disabled={!rejetMotif.trim() || rejeterMutation.isPending}
            onClick={() => rejeterMutation.mutate({ id: rejetId!, motif: rejetMotif.trim() })}
          >
            Confirmer le rejet
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
