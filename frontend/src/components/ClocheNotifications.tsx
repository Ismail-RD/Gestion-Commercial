import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Badge,
  Box,
  Button,
  Divider,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  Popover,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import NotificationsIcon from '@mui/icons-material/Notifications';
import CircleIcon from '@mui/icons-material/Circle';
import {
  compterNonLues,
  lienDuDocument,
  listerNotifications,
  marquerLue,
  marquerToutLu,
  type NiveauNotification,
  type Notification,
} from '../api/notifications';

/** Couleur du point : le niveau se lit avant le texte. */
const COULEUR: Record<NiveauNotification, string> = {
  INFORMATION: 'text.disabled',
  ALERTE: 'warning.main',
  URGENT: 'error.main',
};

/** "il y a 5 min" est plus parlant qu'une heure exacte pour un evenement recent. */
function depuis(iso: string): string {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (minutes < 1) return "a l'instant";
  if (minutes < 60) return `il y a ${minutes} min`;
  const heures = Math.round(minutes / 60);
  if (heures < 24) return `il y a ${heures} h`;
  return new Date(iso).toLocaleDateString('fr-FR');
}

export default function ClocheNotifications() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [ancre, setAncre] = useState<HTMLElement | null>(null);

  // Le compteur est sonde en continu, la liste seulement quand le volet
  // s'ouvre : inutile de rapatrier quinze notifications toutes les minutes
  // pour n'afficher qu'un chiffre.
  const { data: nonLues = 0 } = useQuery({
    queryKey: ['notifications-non-lues'],
    queryFn: compterNonLues,
    refetchInterval: 60_000,
  });

  const { data: liste } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => listerNotifications(15),
    enabled: ancre !== null,
  });

  const rafraichir = () => {
    queryClient.invalidateQueries({ queryKey: ['notifications'] });
    queryClient.invalidateQueries({ queryKey: ['notifications-non-lues'] });
  };

  const lueMutation = useMutation({ mutationFn: marquerLue, onSuccess: rafraichir });
  const toutLuMutation = useMutation({ mutationFn: marquerToutLu, onSuccess: rafraichir });

  const ouvrir = (n: Notification) => {
    if (!n.dateLecture) {
      lueMutation.mutate(n.id);
    }
    const lien = lienDuDocument(n);
    if (lien) {
      navigate(lien);
      setAncre(null);
    }
  };

  const notifications = liste?.content ?? [];

  return (
    <>
      <Tooltip title="Notifications">
        <IconButton color="inherit" onClick={(e) => setAncre(e.currentTarget)} sx={{ mr: 1 }}>
          <Badge badgeContent={nonLues} color="error" max={99}>
            <NotificationsIcon />
          </Badge>
        </IconButton>
      </Tooltip>

      <Popover
        open={ancre !== null}
        anchorEl={ancre}
        onClose={() => setAncre(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{ paper: { sx: { width: 420, maxHeight: 520 } } }}
      >
        <Stack direction="row"
          sx={{ alignItems: 'center', justifyContent: 'space-between', px: 2, py: 1 }}>
          <Typography variant="subtitle1">Notifications</Typography>
          {nonLues > 0 && (
            <Button size="small" onClick={() => toutLuMutation.mutate()}>
              Tout marquer lu
            </Button>
          )}
        </Stack>
        <Divider />

        {notifications.length === 0 ? (
          <Box sx={{ p: 3 }}>
            <Typography variant="body2" color="text.secondary">
              Rien de neuf.
            </Typography>
          </Box>
        ) : (
          <List dense disablePadding>
            {notifications.map((n) => (
              <ListItemButton
                key={n.id}
                onClick={() => ouvrir(n)}
                sx={{
                  alignItems: 'flex-start',
                  // Le non-lu se distingue par le fond, pas par une pastille de
                  // plus : la liste doit rester lisible d'un coup d'oeil.
                  bgcolor: n.dateLecture ? undefined : 'action.hover',
                }}
              >
                <CircleIcon sx={{ fontSize: 10, mt: 0.9, mr: 1.5, color: COULEUR[n.niveau] }} />
                <ListItemText
                  primary={n.titre}
                  secondary={
                    <>
                      {n.message}
                      <Typography component="span" variant="caption"
                        color="text.disabled" sx={{ display: 'block', mt: 0.5 }}>
                        {depuis(n.dateCreation)}
                      </Typography>
                    </>
                  }
                  slotProps={{
                    primary: {
                      variant: 'body2',
                      sx: { fontWeight: n.dateLecture ? 400 : 600 },
                    },
                    secondary: { variant: 'caption' },
                  }}
                />
              </ListItemButton>
            ))}
          </List>
        )}
      </Popover>
    </>
  );
}
