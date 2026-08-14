import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { consulterInvitation, definirMotDePasse } from '../api/invitations';

const LIBELLES: Record<string, string> = {
  ADMIN: 'Administrateur',
  RESPONSABLE_COMMERCIAL: 'Responsable commercial',
  COMMERCIAL: 'Commercial',
  MAGASINIER: 'Magasinier',
  RESPONSABLE_IMPORT: 'Responsable import',
  COMPTABLE: 'Comptable',
};

/**
 * Page ouverte depuis le lien recu par email : l'invite y choisit son mot de
 * passe. Aucun compte utilisable n'existe encore, la sécurité repose sur le
 * jeton non devinable present dans l'URL.
 */
export default function InvitationPage() {
  const { token = '' } = useParams();
  const navigate = useNavigate();
  const [motDePasse, setMotDePasse] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  const [termine, setTermine] = useState(false);

  const { data: invitation, isLoading, error } = useQuery({
    queryKey: ['invitation', token],
    queryFn: () => consulterInvitation(token),
    retry: false,
  });

  const mutation = useMutation({
    mutationFn: () => definirMotDePasse(token, motDePasse),
    onSuccess: () => setTermine(true),
    onError: (e: unknown) =>
      setErreur(
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
          'Enregistrement impossible',
      ),
  });

  const soumettre = () => {
    if (motDePasse.length < 8) {
      setErreur('Le mot de passe doit contenir au moins 8 caractères');
      return;
    }
    if (motDePasse !== confirmation) {
      setErreur('Les deux mots de passe ne correspondent pas');
      return;
    }
    setErreur(null);
    mutation.mutate();
  };

  const messageLien =
    (error as { response?: { data?: { message?: string } } })?.response?.data?.message
    ?? "Ce lien n'est plus valable.";

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', p: 2 }}>
      <Paper sx={{ p: 4, width: 460, maxWidth: '100%' }}>
        <Typography variant="h5" sx={{ mb: 1 }}>Gestion Commerciale</Typography>

        {isLoading && <Typography>Vérification du lien...</Typography>}

        {error && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {messageLien} Demandez a l'administrateur de vous renvoyer une invitation.
          </Alert>
        )}

        {termine && (
          <Stack spacing={2} sx={{ mt: 2 }}>
            <Alert severity="success">
              Votre mot de passe est enregistre. Votre compte est desormais actif.
            </Alert>
            <Button variant="contained" onClick={() => navigate('/login')}>
              Se connecter
            </Button>
          </Stack>
        )}

        {invitation && !termine && (
          <Stack spacing={2} sx={{ mt: 2 }}>
            <Typography variant="body2" color="text.secondary">
              Bonjour {invitation.prenom} {invitation.nom}, choisissez le mot de passe qui
              vous servira a vous connecter avec l'adresse <strong>{invitation.email}</strong>
              {' '}en tant que {LIBELLES[invitation.role] ?? invitation.role}.
            </Typography>

            <TextField
              label="Mot de passe"
              type="password"
              value={motDePasse}
              onChange={(e) => setMotDePasse(e.target.value)}
              helperText="8 caractères minimum"
              autoComplete="new-password"
              required
            />
            <TextField
              label="Confirmer le mot de passe"
              type="password"
              value={confirmation}
              onChange={(e) => setConfirmation(e.target.value)}
              autoComplete="new-password"
              required
              onKeyDown={(e) => { if (e.key === 'Enter') soumettre(); }}
            />

            {erreur && <Alert severity="error">{erreur}</Alert>}

            <Button variant="contained" onClick={soumettre} disabled={mutation.isPending}>
              Terminer mon inscription
            </Button>
          </Stack>
        )}
      </Paper>
    </Box>
  );
}
