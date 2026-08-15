import { useEffect, useState, type ReactNode } from 'react';
import { Link as RouterLink, useLocation } from 'react-router-dom';
import {
  AppBar,
  Avatar,
  Box,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import Inventory2Icon from '@mui/icons-material/Inventory2';
import CategoryIcon from '@mui/icons-material/Category';
import PeopleIcon from '@mui/icons-material/People';
import DescriptionIcon from '@mui/icons-material/Description';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import ReceiptIcon from '@mui/icons-material/Receipt';
import WarehouseIcon from '@mui/icons-material/Warehouse';
import BrandingWatermarkIcon from '@mui/icons-material/BrandingWatermark';
import LocalShippingIcon from '@mui/icons-material/LocalShipping';
import SettingsIcon from '@mui/icons-material/Settings';
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts';
import InsightsIcon from '@mui/icons-material/Insights';
import ShoppingBasketIcon from '@mui/icons-material/ShoppingBasket';
import LogoutIcon from '@mui/icons-material/Logout';
import MenuIcon from '@mui/icons-material/Menu';
import { useAuth } from '../auth/AuthContext';
import { droits, type Droits } from '../auth/droits';
import ClocheNotifications from './ClocheNotifications';
import Marque from './Marque';

const DRAWER_WIDTH = 248;

/** Le rôle tel qu'on l'écrit à l'écran, plutôt que la constante technique. */
const ROLES_LISIBLES: Record<string, string> = {
  ADMIN: 'Administrateur',
  RESPONSABLE_COMMERCIAL: 'Responsable commercial',
  COMMERCIAL: 'Commercial',
  MAGASINIER: 'Magasinier',
  COMPTABLE: 'Comptable',
  RESPONSABLE_IMPORT: 'Responsable import',
};

/** `visible` décide de l'affichage selon les droits ; absent = visible par tous. */
const NAV: {
  to: string;
  label: string;
  icon: ReactNode;
  visible?: (d: Droits) => boolean;
}[] = [
  // Point d'entrée de chacun : ce qu'il a a faire, avant tout le reste.
  { to: '/tableau-de-bord', label: 'Tableau de bord', icon: <InsightsIcon /> },
  { to: '/produits', label: 'Produits', icon: <Inventory2Icon /> },
  { to: '/categories', label: 'Catégories', icon: <CategoryIcon /> },
  { to: '/clients', label: 'Clients', icon: <PeopleIcon />, visible: (d) => d.lireCommercial },
  { to: '/devis', label: 'Devis', icon: <DescriptionIcon />, visible: (d) => d.lireCommercial },
  { to: '/commandes', label: 'Commandes', icon: <ShoppingCartIcon />, visible: (d) => d.lireCommercial },
  { to: '/factures', label: 'Factures', icon: <ReceiptIcon />, visible: (d) => d.lireCommercial },
  { to: '/stock', label: 'Stock', icon: <WarehouseIcon />, visible: (d) => d.voirStock },
  { to: '/marques', label: 'Marques', icon: <BrandingWatermarkIcon /> },
  { to: '/fournisseurs', label: 'Fournisseurs', icon: <LocalShippingIcon />, visible: (d) => d.voirFournisseurs },
  { to: '/commandes-fournisseur', label: 'Achats', icon: <ShoppingBasketIcon />, visible: (d) => d.voirFournisseurs },
  { to: '/utilisateurs', label: 'Utilisateurs', icon: <ManageAccountsIcon />, visible: (d) => d.administrer },
  { to: '/parametres', label: 'Paramètres', icon: <SettingsIcon />, visible: (d) => d.administrer },
];

export default function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const location = useLocation();
  const theme = useTheme();
  // On masque ce que le serveur refuserait, pour ne pas mener a une page vide.
  const mesDroits = droits(user?.role);
  const navVisible = NAV.filter((item) => !item.visible || item.visible(mesDroits));

  /**
   * L'entrée courante, et ses pages filles : /produits/3 garde "Produits"
   * allume. La frontiere sur le slash est indispensable, sans quoi
   * /commandes-fournisseur allumerait aussi "Commandes".
   */
  const estActif = (chemin: string) =>
    location.pathname === chemin || location.pathname.startsWith(`${chemin}/`);

  // Le titre de la barre suit la navigation : on sait toujours où l'on est.
  const rubrique = navVisible.find((item) => estActif(item.to))?.label ?? '';

  // Le menu occupe une largeur fixe qu'un telephone n'a pas : en dessous de
  // 900 px il devient un tiroir qui s'ouvre a la demande.
  const surGrandEcran = useMediaQuery(theme.breakpoints.up('md'));
  const [tiroirOuvert, setTiroirOuvert] = useState(false);

  // La navigation referme le tiroir : sur mobile il recouvre la page, et le
  // laisser ouvert cacherait l'ecran qu'on vient de demander.
  useEffect(() => {
    setTiroirOuvert(false);
  }, [location.pathname]);

  const contenuDuMenu = (
    <>
      <Marque />
      <Divider sx={{ mx: 2 }} />
      <Box sx={{ overflowY: 'auto', py: 1 }}>
        <List disablePadding>
          {navVisible.map((item) => (
            <ListItemButton
              key={item.to}
              component={RouterLink}
              to={item.to}
              selected={estActif(item.to)}
            >
              <ListItemIcon sx={{ minWidth: 38 }}>{item.icon}</ListItemIcon>
              <ListItemText
                primary={item.label}
                slotProps={{ primary: { variant: 'body2', sx: { fontWeight: 500 } } }}
              />
            </ListItemButton>
          ))}
        </List>
      </Box>
    </>
  );

  return (
    <Box sx={{ display: 'flex' }}>
      {/* Sur ordinateur le menu reste affiche en permanence ; ailleurs il
          s'efface et revient a la demande, par-dessus la page. */}
      <Box component="nav" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
        <Drawer
          variant={surGrandEcran ? 'permanent' : 'temporary'}
          open={surGrandEcran || tiroirOuvert}
          onClose={() => setTiroirOuvert(false)}
          // Sur mobile, garder le tiroir monte accelere les ouvertures suivantes.
          ModalProps={{ keepMounted: true }}
          sx={{
            [`& .MuiDrawer-paper`]: { width: DRAWER_WIDTH, boxSizing: 'border-box' },
          }}
        >
          {contenuDuMenu}
        </Drawer>
      </Box>

      <AppBar
        position="fixed"
        sx={{
          width: { xs: '100%', md: `calc(100% - ${DRAWER_WIDTH}px)` },
          ml: { md: `${DRAWER_WIDTH}px` },
        }}
      >
        <Toolbar sx={{ gap: 1 }}>
          {!surGrandEcran && (
            <IconButton edge="start" color="inherit" onClick={() => setTiroirOuvert(true)}>
              <MenuIcon />
            </IconButton>
          )}
          <Typography variant="h6" noWrap sx={{ flexGrow: 1 }}>
            {rubrique}
          </Typography>
          {user && (
            <Stack direction="row" spacing={{ xs: 0.5, sm: 1.5 }} sx={{ alignItems: 'center' }}>
              <ClocheNotifications />
              {/* Le nom et le role prennent trop de place sur un telephone :
                  l'avatar suffit a dire qui est connecte. */}
              <Box sx={{ textAlign: 'right', lineHeight: 1.2, display: { xs: 'none', sm: 'block' } }}>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  {user.prenom} {user.nom}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {ROLES_LISIBLES[user.role] ?? user.role}
                </Typography>
              </Box>
              <Tooltip title={`${user.prenom} ${user.nom}`}>
                <Avatar sx={{ bgcolor: 'primary.main', width: 36, height: 36, fontSize: '0.9rem' }}>
                  {(user.prenom?.[0] ?? '') + (user.nom?.[0] ?? '')}
                </Avatar>
              </Tooltip>
              <Tooltip title="Se déconnecter">
                <IconButton onClick={logout} color="inherit">
                  <LogoutIcon />
                </IconButton>
              </Tooltip>
            </Stack>
          )}
        </Toolbar>
      </AppBar>

      <Box
        component="main"
        sx={{ flexGrow: 1, p: { xs: 1.5, sm: 2, md: 3 }, minHeight: '100vh', width: 0 }}
      >
        <Toolbar />
        {children}
      </Box>
    </Box>
  );
}
