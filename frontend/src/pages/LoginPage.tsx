import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  TextField,
  Typography,
} from '@mui/material';
import { useAuth } from '../auth/AuthContext';
import Marque from '../components/Marque';
import { BLEU_MARQUE } from '../theme';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  // Champ vide : pre-remplir une adresse revenait a designer un compte a tout
  // visiteur de la page de connexion.
  const [email, setEmail] = useState('');
  const [motDePasse, setMotDePasse] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setErreur(null);
    setLoading(true);
    try {
      await login(email, motDePasse);
      navigate('/produits');
    } catch {
      setErreur('Email ou mot de passe incorrect');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        p: 2,
        // Le bleu de la marque en fond : c'est le premier écran, il doit dire
        // tout de suite chez qui l'on entre.
        backgroundColor: 'background.default',
        backgroundImage:
          `radial-gradient(1000px 520px at 15% -10%, ${BLEU_MARQUE}2E 0%, transparent 60%),`
          + ' radial-gradient(800px 460px at 110% 110%, #0284C72E 0%, transparent 55%)',
      }}
    >
      <Card sx={{ width: 420 }}>
        <CardContent sx={{ p: 4 }}>
          <Marque taille="grande" />
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{ mt: 2, mb: 3, textAlign: 'center' }}
          >
            Connectez-vous à votre espace
          </Typography>

          <form onSubmit={handleSubmit}>
            {/* autoComplete laisse le gestionnaire de mots de passe du
                navigateur proposer les identifiants enregistres, ce qu'un champ
                pre-rempli en dur empechait. */}
            <TextField
              label="Email"
              type="email"
              autoComplete="username"
              autoFocus
              fullWidth
              margin="normal"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
            <TextField
              label="Mot de passe"
              type="password"
              autoComplete="current-password"
              fullWidth
              margin="normal"
              value={motDePasse}
              onChange={(e) => setMotDePasse(e.target.value)}
              required
            />
            {erreur && (
              <Alert severity="error" sx={{ mt: 2 }}>
                {erreur}
              </Alert>
            )}
            <Button
              type="submit"
              variant="contained"
              fullWidth
              size="large"
              sx={{ mt: 3 }}
              disabled={loading}
            >
              {loading ? <CircularProgress size={24} /> : 'Se connecter'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </Box>
  );
}
