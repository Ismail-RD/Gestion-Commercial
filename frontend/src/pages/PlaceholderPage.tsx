import { Box, Paper, Typography } from '@mui/material';
import ConstructionIcon from '@mui/icons-material/Construction';

export default function PlaceholderPage({ titre }: { titre: string }) {
  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 2 }}>
        {titre}
      </Typography>
      <Paper sx={{ p: 6, textAlign: 'center', color: 'text.secondary' }}>
        <ConstructionIcon sx={{ fontSize: 48, mb: 1 }} />
        <Typography>Écran en cours de construction</Typography>
      </Paper>
    </Box>
  );
}
