import { useState } from 'react';
import { libelle, STATUT_DOCUMENT, TYPE_TIERS } from '../utils/libelles';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import LockOpenIcon from '@mui/icons-material/LockOpen';
import LockIcon from '@mui/icons-material/Lock';
import CreditScoreIcon from '@mui/icons-material/CreditScore';
import { bloquerClient, debloquerClient, definirPlafondClient, trouverClient } from '../api/clients';
import { listerDevis } from '../api/devis';
import { listerCommandes } from '../api/commandes';
import { listerFactures } from '../api/factures';
import { useAuth } from '../auth/AuthContext';
import { droits } from '../auth/droits';
import { formatMontant } from '../utils/format';
import { Champ, Champs, DetailLayout, Section } from '../components/DetailView';

export default function ClientDetailPage() {
  const { id } = useParams();
  const clientId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  // Plafond, blocage et reattribution relevent de l'encadrement commercial
  const estAdmin = droits(user?.role).encadrer;

  const { data: client, isLoading, isError } = useQuery({
    queryKey: ['client', clientId],
    queryFn: () => trouverClient(clientId),
    enabled: !Number.isNaN(clientId),
  });

  const blocageMutation = useMutation({
    mutationFn: (bloquer: boolean) => (bloquer ? bloquerClient(clientId) : debloquerClient(clientId)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['client', clientId] }),
  });

  // Definition du plafond de crédit (après création)
  const [plafondOpen, setPlafondOpen] = useState(false);
  const [plafondValeur, setPlafondValeur] = useState('');
  const plafondMutation = useMutation({
    // Champ vide = aucun crédit accorde, soit un plafond a 0.
    mutationFn: () =>
      definirPlafondClient(clientId, plafondValeur.trim() === '' ? 0 : Number(plafondValeur)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['client', clientId] });
      setPlafondOpen(false);
    },
  });

  // Documents commerciaux du client
  const { data: devis } = useQuery({
    queryKey: ['client-devis', clientId],
    queryFn: () => listerDevis({ clientId, size: 100, sort: 'dateCreation,desc' }),
    enabled: !Number.isNaN(clientId),
  });
  const { data: commandes } = useQuery({
    queryKey: ['client-commandes', clientId],
    queryFn: () => listerCommandes({ clientId, size: 100, sort: 'dateCommande,desc' }),
    enabled: !Number.isNaN(clientId),
  });
  const { data: factures } = useQuery({
    queryKey: ['client-factures', clientId],
    queryFn: () => listerFactures({ clientId, size: 100, sort: 'dateFacture,desc' }),
    enabled: !Number.isNaN(clientId),
  });

  const estEntreprise = client?.typeClient === 'ENTREPRISE';

  return (
    <DetailLayout
      titre={client?.nom ?? 'Client'}
      sousTitre={
        client && (
          <Chip
            label={libelle(TYPE_TIERS, client.typeClient)}
            size="small"
            color={estEntreprise ? 'primary' : 'default'}
          />
        )
      }
      retour="/clients"
      isLoading={isLoading}
      isError={isError}
    >
      {client && (
        <>
          <Section titre="Coordonnees">
            <Champs>
              <Champ label="Nom" valeur={client.nom} />
              <Champ label="Email" valeur={client.email} />
              <Champ
                label="Téléphones"
                valeur={client.telephones?.length ? client.telephones.join(', ') : undefined}
              />
              <Champ label="Adresse" valeur={client.adresse} />
              <Champ label="Commercial en charge" valeur={client.commercialNom} />
              <Champ
                label="Client depuis"
                valeur={
                  client.dateCreation
                    ? new Date(client.dateCreation).toLocaleDateString('fr-FR')
                    : undefined
                }
              />
              <Champ
                label="RIB"
                valeur={
                  client.ribs?.length
                    ? client.ribs
                        .map((r) => (r.banque ? `${r.rib} (${r.banque})` : r.rib))
                        .join(' · ')
                    : undefined
                }
              />
            </Champs>
          </Section>

          <Section titre="Credit">
            <Stack
              direction="row"
              sx={{ justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 2 }}
            >
              <Champs>
                <Champ
                  label="Statut"
                  valeur={
                    <Chip
                      label={client.statut === 'BLOQUE' ? 'Bloqué' : 'Actif'}
                      size="small"
                      color={client.statut === 'BLOQUE' ? 'error' : 'success'}
                    />
                  }
                />
                <Champ
                  label="Plafond de crédit"
                  valeur={
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <span>{formatMontant(client.plafondCredit ?? 0)}</span>
                      {/* Sans autorisation de credit, toute facture impayee bloque */}
                      {!client.plafondCredit && (
                        <Chip label="Aucun crédit accorde" size="small" color="warning" variant="outlined" />
                      )}
                    </Stack>
                  }
                />
                <Champ
                  label="Encours (factures impayées)"
                  valeur={client.encours != null ? formatMontant(client.encours) : undefined}
                />
              </Champs>
              {estAdmin && (
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="outlined"
                    startIcon={<CreditScoreIcon />}
                    onClick={() => {
                      setPlafondValeur(client.plafondCredit != null ? String(client.plafondCredit) : '');
                      setPlafondOpen(true);
                    }}
                  >
                    Definir le plafond
                  </Button>
                  <Button
                    variant="outlined"
                    color={client.statut === 'BLOQUE' ? 'success' : 'warning'}
                    startIcon={client.statut === 'BLOQUE' ? <LockOpenIcon /> : <LockIcon />}
                    disabled={blocageMutation.isPending}
                    onClick={() => blocageMutation.mutate(client.statut !== 'BLOQUE')}
                  >
                    {client.statut === 'BLOQUE' ? 'Débloquer' : 'Bloquer'}
                  </Button>
                </Stack>
              )}
            </Stack>
          </Section>

          {estEntreprise ? (
            <Section titre="Entreprise">
              <Champs>
                <Champ label="Raison sociale" valeur={client.raisonSociale} />
                <Champ label="ICE" valeur={client.ice} />
                <Champ label="Identifiant fiscal" valeur={client.identifiantFiscal} />
                <Champ
                  label="Contact"
                  valeur={
                    client.contactNom
                      ? `${client.contactPrenom ?? ''} ${client.contactNom}`.trim()
                      : undefined
                  }
                />
              </Champs>
            </Section>
          ) : (
            <Section titre="Particulier">
              <Champs>
                <Champ label="Prénom" valeur={client.prenom} />
                <Champ label="CIN" valeur={client.cin} />
                <Champ
                  label="Date de naissance"
                  valeur={
                    client.dateNaissance
                      ? new Date(client.dateNaissance).toLocaleDateString('fr-FR')
                      : undefined
                  }
                />
              </Champs>
            </Section>
          )}

          <Section titre={`Devis (${devis?.totalElements ?? 0})`}>
            <DocumentsTable
              lignes={(devis?.content ?? []).map((d) => ({
                id: d.id,
                numero: d.numero,
                date: d.dateCreation,
                montant: d.montantTTC,
                statut: d.statut,
              }))}
              onClick={() => navigate('/devis')}
            />
          </Section>

          <Section titre={`Commandes (${commandes?.totalElements ?? 0})`}>
            <DocumentsTable
              lignes={(commandes?.content ?? []).map((c) => ({
                id: c.id,
                numero: c.numero,
                date: c.dateCommande,
                montant: c.montantTTC,
                statut: c.statut,
              }))}
              onClick={() => navigate('/commandes')}
            />
          </Section>

          <Section titre={`Factures (${factures?.totalElements ?? 0})`}>
            <DocumentsTable
              lignes={(factures?.content ?? []).map((f) => ({
                id: f.id,
                numero: f.numero,
                date: f.dateFacture,
                montant: f.montantTTC,
                statut: f.statut,
              }))}
              onClick={() => navigate('/factures')}
            />
          </Section>
        </>
      )}

      {/* Definition du plafond de credit, apres la creation du client */}
      <Dialog open={plafondOpen} onClose={() => setPlafondOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Plafond de crédit</DialogTitle>
        <DialogContent>
          <TextField
            label="Plafond (DH)"
            type="number"
            fullWidth
            value={plafondValeur}
            onChange={(e) => setPlafondValeur(e.target.value)}
            helperText="Laisser vide ou 0 : aucun crédit accorde, le client est bloque des la 1ere facturé impayée"
            slotProps={{ inputLabel: { shrink: true } }}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPlafondOpen(false)}>Annuler</Button>
          <Button
            variant="contained"
            disabled={plafondMutation.isPending}
            onClick={() => plafondMutation.mutate()}
          >
            Enregistrer
          </Button>
        </DialogActions>
      </Dialog>
    </DetailLayout>
  );
}

interface LigneDocument {
  id: number;
  numero: string;
  date?: string;
  montant?: number;
  statut: string;
}

function DocumentsTable({ lignes, onClick }: { lignes: LigneDocument[]; onClick: () => void }) {
  if (lignes.length === 0) {
    return <Typography color="text.secondary">Aucun document</Typography>;
  }
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Numéro</TableCell>
          <TableCell>Date</TableCell>
          <TableCell align="right">Montant TTC</TableCell>
          <TableCell>Statut</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {lignes.map((l) => (
          <TableRow key={l.id} hover sx={{ cursor: 'pointer' }} onClick={onClick}>
            <TableCell>{l.numero}</TableCell>
            <TableCell>{l.date ? new Date(l.date).toLocaleDateString('fr-FR') : '-'}</TableCell>
            <TableCell align="right">{formatMontant(l.montant)}</TableCell>
            <TableCell><Chip label={libelle(STATUT_DOCUMENT, l.statut)} size="small" variant="outlined" /></TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
