import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import IconeRubrique from '@mui/icons-material/Settings';
import EnTetePage from '../components/EnTetePage';
import SaveIcon from '@mui/icons-material/Save';
import SectionDepots from '../components/SectionDepots';
import { listerPouvoirs, modifierPouvoir, type PouvoirRole } from '../api/parametres';
import type { Role } from '../api/types';

const LIBELLES: Partial<Record<Role, string>> = {
  RESPONSABLE_COMMERCIAL: 'Responsable commercial',
  COMMERCIAL: 'Commercial',
};

/** Valeurs en cours de saisie, en chaines tant que l'utilisateur tape. */
type Saisie = { seuil: string; plafond: string };

export default function ParametresPage() {
  const queryClient = useQueryClient();
  const [saisies, setSaisies] = useState<Record<string, Saisie>>({});
  const [erreur, setErreur] = useState<string | null>(null);
  const [succes, setSucces] = useState<string | null>(null);

  const { data: pouvoirs, isLoading, isError } = useQuery({
    queryKey: ['pouvoirs'],
    queryFn: listerPouvoirs,
  });

  // Les champs partent des valeurs enregistrées, et s'y recalent après un envoi.
  useEffect(() => {
    if (pouvoirs) {
      setSaisies(Object.fromEntries(pouvoirs.map((p) => [p.role, ligne(p)])));
    }
  }, [pouvoirs]);

  const mutation = useMutation({
    mutationFn: ({ role, seuilRemisePct, plafondCreditMax }: {
      role: Role; seuilRemisePct: number; plafondCreditMax: number | null;
    }) => modifierPouvoir(role, { seuilRemisePct, plafondCreditMax }),
    onSuccess: (p) => {
      queryClient.invalidateQueries({ queryKey: ['pouvoirs'] });
      setErreur(null);
      setSucces(`Pouvoirs du rôle ${LIBELLES[p.role] ?? p.role} enregistres`);
    },
    onError: (e: unknown) => {
      setSucces(null);
      setErreur(
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
          'Enregistrement impossible',
      );
    },
  });

  const enregistrer = (p: PouvoirRole) => {
    const saisie = saisies[p.role];
    const seuil = Number(saisie.seuil);
    if (!Number.isFinite(seuil) || seuil < 0 || seuil >= 100) {
      setSucces(null);
      setErreur('Le seuil de remise doit être compris entre 0 et 99,99 %');
      return;
    }
    // Un rôle sans plafond de crédit n'en recoit pas : le champ reste absent.
    let plafond: number | null = null;
    if (accordeDuCredit(p)) {
      plafond = Number(saisie.plafond);
      if (!Number.isFinite(plafond) || plafond < 0) {
        setSucces(null);
        setErreur('Le plafond de crédit doit être un montant positif');
        return;
      }
    }
    mutation.mutate({ role: p.role, seuilRemisePct: seuil, plafondCreditMax: plafond });
  };

  return (
    <Box>
      <Box sx={{ mb: 3 }}>
        <EnTetePage titre="Paramètres" icone={<IconeRubrique />} />
      </Box>

      <Paper sx={{ p: 3 }}>
        <Typography variant="h6">Pouvoirs par rôle</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1, mb: 2 }}>
          Ce que chaque rôle engage seul. Une remise au-delà du seuil place le devis ou la
          commande en attente de validation : ni envoi, ni impression, ni facturation tant
          que la hiérarchie n'a pas tranché. Le seuil borne aussi ce que son titulaire peut
          valider chez les autres. Le plafond de crédit limite le crédit qu'il accorde à un
          client. L'administrateur n'est borné par rien.
        </Typography>

        {isLoading && <Typography>Chargement...</Typography>}
        {isError && <Alert severity="error">Erreur de chargement des paramètres</Alert>}
        {erreur && <Alert severity="error" sx={{ mb: 2 }}>{erreur}</Alert>}
        {succes && <Alert severity="success" sx={{ mb: 2 }}>{succes}</Alert>}

        {pouvoirs && (
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Rôle</TableCell>
                  <TableCell sx={{ width: 180 }}>Seuil de remise (%)</TableCell>
                  <TableCell sx={{ width: 220 }}>Plafond de crédit (DH)</TableCell>
                  <TableCell align="right" sx={{ width: 160 }} />
                </TableRow>
              </TableHead>
              <TableBody>
                {pouvoirs.map((p) => (
                  <TableRow key={p.role}>
                    <TableCell>{LIBELLES[p.role] ?? p.role}</TableCell>
                    <TableCell>
                      <TextField
                        size="small"
                        type="number"
                        value={saisies[p.role]?.seuil ?? ''}
                        onChange={(e) => majSaisie(p.role, { seuil: e.target.value })}
                        slotProps={{ htmlInput: { min: 0, max: 99.99, step: 0.5 } }}
                      />
                    </TableCell>
                    <TableCell>
                      {accordeDuCredit(p) ? (
                        <TextField
                          size="small"
                          type="number"
                          value={saisies[p.role]?.plafond ?? ''}
                          onChange={(e) => majSaisie(p.role, { plafond: e.target.value })}
                          slotProps={{ htmlInput: { min: 0, step: 1000 } }}
                        />
                      ) : (
                        <Typography variant="body2" color="text.secondary">
                          N'accorde pas de credit
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell align="right">
                      <Button
                        size="small"
                        variant="contained"
                        startIcon={<SaveIcon />}
                        disabled={mutation.isPending || inchange(p)}
                        onClick={() => enregistrer(p)}
                      >
                        Enregistrer
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      <SectionDepots />
    </Box>
  );

  function majSaisie(role: Role, champs: Partial<Saisie>) {
    setSaisies({ ...saisies, [role]: { ...saisies[role], ...champs } });
  }

  function inchange(p: PouvoirRole) {
    const saisie = saisies[p.role];
    const initiale = ligne(p);
    return !saisie || (saisie.seuil === initiale.seuil && saisie.plafond === initiale.plafond);
  }
}

function ligne(p: PouvoirRole): Saisie {
  return {
    seuil: String(p.seuilRemisePct),
    plafond: p.plafondCreditMax === null ? '' : String(p.plafondCreditMax),
  };
}

/** Le commercial ne fixe pas les plafonds : le backend n'en accepte pas pour lui. */
function accordeDuCredit(p: PouvoirRole) {
  return p.role !== 'COMMERCIAL';
}
