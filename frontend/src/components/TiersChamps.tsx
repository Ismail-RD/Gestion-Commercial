import { Box, Button, IconButton, Stack, TextField, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import type { RibDto } from '../api/types';

/**
 * Editeurs de listes partages par les formulaires client et fournisseur :
 * un tiers peut avoir plusieurs telephones et plusieurs RIB.
 */

export function EditeurTelephones({
  valeurs,
  onChange,
}: {
  valeurs: string[] | undefined;
  onChange: (v: string[]) => void;
}) {
  const liste = valeurs ?? [];
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">
        Telephones
      </Typography>
      <Stack spacing={1} sx={{ mt: 0.5 }}>
        {liste.map((tel, i) => (
          <Stack direction="row" spacing={1} key={i}>
            <TextField
              size="small"
              fullWidth
              label={`Telephone ${i + 1}`}
              value={tel}
              onChange={(e) => {
                const copie = [...liste];
                copie[i] = e.target.value;
                onChange(copie);
              }}
            />
            <IconButton
              aria-label="Supprimer ce téléphone"
              onClick={() => onChange(liste.filter((_, j) => j !== i))}
            >
              <DeleteIcon fontSize="small" />
            </IconButton>
          </Stack>
        ))}
        <Button size="small" startIcon={<AddIcon />} onClick={() => onChange([...liste, ''])}>
          Ajouter un telephone
        </Button>
      </Stack>
    </Box>
  );
}

export function EditeurRibs({
  valeurs,
  onChange,
}: {
  valeurs: RibDto[] | undefined;
  onChange: (v: RibDto[]) => void;
}) {
  const liste = valeurs ?? [];
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">
        RIB
      </Typography>
      <Stack spacing={1} sx={{ mt: 0.5 }}>
        {liste.map((rib, i) => (
          <Stack direction="row" spacing={1} key={i}>
            <TextField
              size="small"
              label="RIB"
              value={rib.rib ?? ''}
              onChange={(e) => {
                const copie = [...liste];
                copie[i] = { ...copie[i], rib: e.target.value };
                onChange(copie);
              }}
              sx={{ flex: 2 }}
            />
            <TextField
              size="small"
              label="Banque"
              value={rib.banque ?? ''}
              onChange={(e) => {
                const copie = [...liste];
                copie[i] = { ...copie[i], banque: e.target.value };
                onChange(copie);
              }}
              sx={{ flex: 1 }}
            />
            <IconButton
              aria-label="Supprimer ce RIB"
              onClick={() => onChange(liste.filter((_, j) => j !== i))}
            >
              <DeleteIcon fontSize="small" />
            </IconButton>
          </Stack>
        ))}
        <Button size="small" startIcon={<AddIcon />} onClick={() => onChange([...liste, { rib: '', banque: '' }])}>
          Ajouter un RIB
        </Button>
      </Stack>
    </Box>
  );
}
