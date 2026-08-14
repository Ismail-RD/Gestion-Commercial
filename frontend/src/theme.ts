import { createTheme, alpha } from '@mui/material/styles';

/**
 * Charte de l'application, tirée du logo SOGETHERM : le bleu ciel de la marque,
 * accompagné de l'orange et du vert du pictogramme.
 *
 * <p>Un point mérite d'être expliqué. Le bleu du logo (#29ABE2) est trop clair
 * pour porter du texte blanc : sur un bouton, le contraste tombe sous le seuil
 * de lisibilité. Il reste donc la couleur « décor » · dégradés, graphiques,
 * aplats · pendant qu'une déclinaison plus soutenue sert aux boutons et aux
 * liens. La page garde ainsi l'identité de l'entreprise sans devenir pénible à
 * lire sur un écran d'atelier ou en plein soleil.
 */

/** Bleu ciel du logo. Réservé aux surfaces décoratives et aux graphiques. */
export const BLEU_MARQUE = '#29ABE2';

/** Les deux couleurs du pictogramme, utilisées comme accents. */
export const ORANGE_MARQUE = '#F1662A';
export const VERT_MARQUE = '#2BB673';

const theme = createTheme({
  palette: {
    primary: {
      main: '#0284C7',
      light: BLEU_MARQUE,
      dark: '#075985',
      contrastText: '#FFFFFF',
    },
    secondary: {
      main: ORANGE_MARQUE,
      light: '#FF8A57',
      dark: '#C24714',
      contrastText: '#FFFFFF',
    },
    success: { main: '#15A46B', light: VERT_MARQUE },
    warning: { main: '#E08600' },
    error: { main: '#D92D20' },
    info: { main: '#0EA5E9' },
    background: {
      // Un fond très légèrement bleuté : les cartes blanches s'en détachent
      // sans avoir besoin d'ombres lourdes.
      default: '#F3F8FC',
      paper: '#FFFFFF',
    },
    text: {
      primary: '#0F2A3D',
      secondary: '#587184',
    },
    divider: '#E3ECF3',
  },

  shape: { borderRadius: 12 },

  typography: {
    fontFamily: [
      '"Segoe UI"', 'Inter', 'Roboto', '"Helvetica Neue"', 'Arial', 'sans-serif',
    ].join(','),
    h4: { fontWeight: 700, letterSpacing: '-0.02em' },
    h5: { fontWeight: 700, letterSpacing: '-0.01em' },
    h6: { fontWeight: 600 },
    subtitle1: { fontWeight: 600 },
    subtitle2: { fontWeight: 600 },
    button: { textTransform: 'none', fontWeight: 600 },
  },

  components: {
    MuiCssBaseline: {
      styleOverrides: {
        // Les tableaux larges défilent dans leur conteneur, jamais la page.
        body: { overflowX: 'hidden' },
        '*::-webkit-scrollbar': { width: 10, height: 10 },
        '*::-webkit-scrollbar-thumb': {
          backgroundColor: '#C9DBE8', borderRadius: 8, border: '2px solid transparent',
          backgroundClip: 'content-box',
        },
      },
    },

    MuiPaper: {
      styleOverrides: {
        // Une ombre douce plutôt qu'un trait : la hiérarchie se lit sans bruit.
        rounded: { borderRadius: 14 },
        elevation1: {
          boxShadow: '0 1px 2px rgba(15, 42, 61, 0.04), 0 8px 24px rgba(15, 42, 61, 0.06)',
        },
      },
    },

    MuiAppBar: {
      defaultProps: { elevation: 0, color: 'inherit' },
      styleOverrides: {
        root: {
          backgroundColor: '#FFFFFF',
          borderBottom: '1px solid #E3ECF3',
          color: '#0F2A3D',
        },
      },
    },

    MuiDrawer: {
      styleOverrides: {
        paper: { backgroundColor: '#FFFFFF', borderRight: '1px solid #E3ECF3' },
      },
    },

    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { borderRadius: 10, paddingInline: 16 },
        contained: ({ ownerState }) => ({
          // Un dégradé très court : du relief, sans effet « bouton 3D ».
          ...(ownerState.color === 'primary' && {
            backgroundImage: `linear-gradient(180deg, ${BLEU_MARQUE} 0%, #0284C7 100%)`,
          }),
        }),
      },
    },

    MuiListItemButton: {
      styleOverrides: {
        root: {
          borderRadius: 10,
          marginInline: 8,
          marginBlock: 2,
          '&.Mui-selected': {
            backgroundColor: alpha(BLEU_MARQUE, 0.12),
            color: '#0284C7',
            '& .MuiListItemIcon-root': { color: '#0284C7' },
            '&:hover': { backgroundColor: alpha(BLEU_MARQUE, 0.18) },
          },
        },
      },
    },

    MuiTableHead: {
      styleOverrides: {
        root: {
          '& .MuiTableCell-head': {
            backgroundColor: '#F7FBFE',
            color: '#587184',
            fontWeight: 600,
            fontSize: '0.78rem',
            letterSpacing: '0.03em',
            textTransform: 'uppercase',
            borderBottom: '1px solid #E3ECF3',
          },
        },
      },
    },

    MuiTableRow: {
      styleOverrides: {
        root: { '&:last-child td': { borderBottom: 0 } },
      },
    },

    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 600, borderRadius: 8 },
        sizeSmall: { height: 22 },
      },
    },

    MuiAlert: {
      styleOverrides: { root: { borderRadius: 12 } },
    },

    MuiDialog: {
      styleOverrides: { paper: { borderRadius: 16 } },
    },

    MuiTextField: {
      defaultProps: { size: 'small' },
    },

    MuiTooltip: {
      styleOverrides: {
        tooltip: { backgroundColor: '#0F2A3D', fontSize: '0.78rem', borderRadius: 8 },
      },
    },

    // La pagination de MUI parle anglais par défaut (« 1-4 of 4 »). Elle se
    // traduit ici, une fois, plutôt que sur chacun des tableaux.
    MuiTablePagination: {
      defaultProps: {
        labelRowsPerPage: 'Lignes par page',
        labelDisplayedRows: ({ from, to, count }) =>
          `${from}–${to} sur ${count !== -1 ? count : `plus de ${to}`}`,
        getItemAriaLabel: (type) =>
          (type === 'previous' ? 'Page précédente' : 'Page suivante'),
      },
    },
  },
});

export default theme;
