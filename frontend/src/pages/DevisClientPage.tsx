import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Divider,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import { useParams } from 'react-router-dom';
import {
  accepterDevisPublic,
  getDevisPublic,
  refuserDevisPublic,
  telechargerDevisPublicPdf,
} from '../api/devisPublic';
import { formatMontant } from '../utils/format';

function messageErreur(err: unknown, defaut: string): string {
  const data = (err as { response?: { data?: { message?: string } } })?.response?.data;
  return data?.message ?? defaut;
}

function formatDate(valeur?: string | null): string {
  return valeur ? new Date(valeur).toLocaleDateString('fr-FR') : '-';
}

/**
 * Espace client, hors authentification : le destinataire du devis arrive ici
 * depuis le lien recu par email. Il consulte le PDF puis accepte (en deposant
 * son bon de commande) ou refuse.
 *
 * Sa reponse est une information transmise a SOGETHERM : la validation du devis
 * reste effectuee manuellement dans l'application.
 */
export default function DevisClientPage() {
  const { token = '' } = useParams();
  const queryClient = useQueryClient();
  const [fichier, setFichier] = useState<File | null>(null);
  const [commentaire, setCommentaire] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const { data: devis, isLoading, isError } = useQuery({
    queryKey: ['devis-public', token],
    queryFn: () => getDevisPublic(token),
    retry: false,
  });

  const rafraichir = () => {
    setErreur(null);
    queryClient.invalidateQueries({ queryKey: ['devis-public', token] });
  };

  const accepterMutation = useMutation({
    mutationFn: () => accepterDevisPublic(token, fichier!),
    onSuccess: rafraichir,
    onError: (err) => setErreur(messageErreur(err, "Envoi impossible, veuillez reessayer")),
  });

  const refuserMutation = useMutation({
    mutationFn: () => refuserDevisPublic(token, commentaire || undefined),
    onSuccess: rafraichir,
    onError: (err) => setErreur(messageErreur(err, "Envoi impossible, veuillez reessayer")),
  });

  const ouvrirPdf = async () => {
    const blob = await telechargerDevisPublicPdf(token);
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  };

  const accepter = () => {
    if (!fichier) {
      setErreur('Veuillez joindre votre bon de commande (PDF, JPG ou PNG) avant de valider.');
      return;
    }
    setErreur(null);
    accepterMutation.mutate();
  };

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 10 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (isError || !devis) {
    return (
      <Container maxWidth="sm" sx={{ mt: 8 }}>
        <Alert severity="error">
          Ce lien n'est pas valide ou n'est plus actif. Merci de contacter votre interlocuteur commercial.
        </Alert>
      </Container>
    );
  }

  const dejaRepondu = devis.reponseClient != null;

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Paper sx={{ p: 4 }}>
        <Typography variant="overline" color="text.secondary">
          {devis.societeNom}
        </Typography>
        <Typography variant="h5" gutterBottom>
          Devis {devis.numero}
        </Typography>

        <Stack spacing={1} sx={{ my: 3 }}>
          <Ligne label="Client" valeur={devis.clientNom ?? '-'} />
          {devis.reference && <Ligne label="Référence" valeur={devis.reference} />}
          <Ligne label="Date" valeur={formatDate(devis.date)} />
          <Ligne label="Valable jusqu'au" valeur={formatDate(devis.dateValidite)} />
          <Divider sx={{ my: 1 }} />
          <Ligne label="Total HT" valeur={formatMontant(devis.montantHT ?? 0)} />
          <Ligne label="Net à payer TTC" valeur={formatMontant(devis.montantTTC ?? 0)} fort />
        </Stack>

        <Button fullWidth variant="outlined" startIcon={<PictureAsPdfIcon />} onClick={ouvrirPdf}>
          Consulter le devis (PDF)
        </Button>

        <Divider sx={{ my: 3 }} />

        {dejaRepondu ? (
          <Stack spacing={2} sx={{ alignItems: 'center' }}>
            <Chip
              color={devis.reponseClient === 'ACCEPTE' ? 'success' : 'default'}
              label={
                devis.reponseClient === 'ACCEPTE'
                  ? `Devis accepte le ${formatDate(devis.dateReponseClient)}`
                  : `Devis refuse le ${formatDate(devis.dateReponseClient)}`
              }
            />
            <Typography variant="body2" color="text.secondary" align="center">
              Votre reponse a bien ete transmise a {devis.societeNom}. Notre equipe revient vers vous
              apres verification.
            </Typography>
          </Stack>
        ) : (
          <Stack spacing={2}>
            <Typography variant="subtitle1">Votre réponse</Typography>
            {erreur && <Alert severity="error">{erreur}</Alert>}

            <Typography variant="body2" color="text.secondary">
              Pour accepter ce devis, merci de joindre votre bon de commande (PDF, JPG ou PNG).
            </Typography>
            <input
              ref={inputRef}
              type="file"
              hidden
              accept="application/pdf,image/jpeg,image/png"
              onChange={(e) => setFichier(e.target.files?.[0] ?? null)}
            />
            <Button
              variant="outlined"
              startIcon={<UploadFileIcon />}
              onClick={() => inputRef.current?.click()}
            >
              {fichier ? fichier.name : 'Joindre le bon de commande'}
            </Button>
            <Button
              variant="contained"
              color="success"
              startIcon={<CheckIcon />}
              disabled={accepterMutation.isPending}
              onClick={accepter}
            >
              Accepter le devis
            </Button>

            <Divider>ou</Divider>

            <TextField
              label="Motif du refus (facultatif)"
              multiline
              minRows={2}
              value={commentaire}
              onChange={(e) => setCommentaire(e.target.value)}
            />
            <Button
              variant="outlined"
              color="error"
              startIcon={<CloseIcon />}
              disabled={refuserMutation.isPending}
              onClick={() => { setErreur(null); refuserMutation.mutate(); }}
            >
              Refuser le devis
            </Button>
          </Stack>
        )}
      </Paper>
    </Container>
  );
}

function Ligne({ label, valeur, fort }: { label: string; valeur: string; fort?: boolean }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2 }}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: fort ? 700 : 400 }}>
        {valeur}
      </Typography>
    </Box>
  );
}
