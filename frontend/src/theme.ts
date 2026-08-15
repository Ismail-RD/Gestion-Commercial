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

/**
 * Couleurs d'un graphique dont les séries n'ont pas d'ordre naturel, dans cet
 * ordre et jamais recyclées.
 *
 * <p>Elle s'ouvre sur les trois couleurs de la maison — bleu, orange, vert —
 * mais pas sur les teintes du logo telles quelles : le bleu ciel et le vert
 * clair tombent sous le seuil de contraste sur fond blanc, où une part de
 * camembert devient un aplat pâle que l'on devine plus qu'on ne le lit. Ce sont
 * donc leurs déclinaisons soutenues.
 *
 * <p><b>Quatre, et pas davantage.</b> Au-delà, deux teintes finissent toujours
 * par se ressembler : le turquoise se confond avec le bleu même en vision
 * normale, le rose avec le vert pour un daltonien. Une cinquième série se
 * regroupe donc sous « Autres », ou se sépare en deux graphiques — elle ne
 * reçoit pas une couleur de plus.
 *
 * <p>Ces quatre valeurs passent les contrôles d'usage, toutes paires
 * confrontées : bande de clarté, saturation minimale, écart pour les
 * daltonismes courants, contraste sur le blanc. Y toucher sans revérifier
 * casserait la lecture pour une partie des lecteurs, sans que rien ne se voie
 * sur un écran ordinaire.
 */
export const PALETTE_GRAPHES = [
  '#0284C7', // bleu de la marque, soutenu
  ORANGE_MARQUE,
  '#15A46B', // vert du pictogramme, soutenu
  '#7C5CFF',
];

/**
 * Échelle d'ancienneté, pour ce qui se dégrade avec le temps : la balance âgée
 * des impayés.
 *
 * <p>Des couleurs sans rapport les unes avec les autres diraient « cinq
 * familles » là où il s'agit d'une même chose qui empire. Le vert isole ce qui
 * n'est pas encore dû ; les quatre tranches de retard suivent un seul dégradé
 * qui fonce à mesure, si bien que la gravité se lit même imprimée en gris.
 */
export const RAMPE_ANCIENNETE = [
  '#15A46B', // non échu
  '#F59E0B',
  '#D97706',
  '#B45309',
  '#7C2D12',
];

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
    // Les titres se resserrent sur petit ecran : un h4 de bureau y mangerait
    // deux lignes a lui seul.
    h4: {
      fontWeight: 700,
      letterSpacing: '-0.02em',
      fontSize: '2.125rem',
      '@media (max-width:600px)': { fontSize: '1.5rem' },
    },
    h5: {
      fontWeight: 700,
      letterSpacing: '-0.01em',
      fontSize: '1.5rem',
      '@media (max-width:600px)': { fontSize: '1.25rem' },
    },
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
        // Deux couches — un contact net, une diffusion large — donnent la
        // profondeur qu'une ombre unique rend toujours un peu sale.
        rounded: { borderRadius: 14 },
        elevation1: {
          boxShadow: '0 1px 2px rgba(15, 42, 61, 0.04), 0 8px 24px rgba(15, 42, 61, 0.06)',
        },
        elevation2: {
          boxShadow: '0 2px 4px rgba(15, 42, 61, 0.06), 0 14px 34px rgba(15, 42, 61, 0.10)',
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
        root: {
          borderRadius: 10,
          paddingInline: 16,
          transition: 'transform .12s ease, box-shadow .12s ease, background-color .12s ease',
        },
        contained: ({ ownerState }) => ({
          // Un dégradé très court : du relief, sans effet « bouton 3D ».
          ...(ownerState.color === 'primary' && {
            backgroundImage: `linear-gradient(180deg, ${BLEU_MARQUE} 0%, #0284C7 100%)`,
            boxShadow: '0 1px 2px rgba(2, 132, 199, 0.28)',
            '&:hover': {
              // Le bouton se soulève d'un pixel : l'action se signale sans
              // qu'aucune couleur ne change.
              transform: 'translateY(-1px)',
              boxShadow: '0 6px 16px rgba(2, 132, 199, 0.32)',
            },
          }),
          ...(ownerState.color === 'secondary' && {
            backgroundImage: `linear-gradient(180deg, #FF7A45 0%, ${ORANGE_MARQUE} 100%)`,
            '&:hover': {
              transform: 'translateY(-1px)',
              boxShadow: '0 6px 16px rgba(241, 102, 42, 0.32)',
            },
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
          position: 'relative',
          transition: 'background-color .15s ease, color .15s ease',
          '& .MuiListItemIcon-root': { color: '#8AA2B3', transition: 'color .15s ease' },
          '&:hover': { backgroundColor: alpha(BLEU_MARQUE, 0.08) },
          '&.Mui-selected': {
            backgroundColor: alpha(BLEU_MARQUE, 0.12),
            color: '#0284C7',
            fontWeight: 600,
            '& .MuiListItemIcon-root': { color: '#0284C7' },
            '&:hover': { backgroundColor: alpha(BLEU_MARQUE, 0.18) },
            // Un bandeau vertical à gauche : la rubrique courante se repère au
            // bord du menu, sans avoir à lire les libellés un à un.
            '&::before': {
              content: '""',
              position: 'absolute',
              left: -8,
              top: 8,
              bottom: 8,
              width: 3,
              borderRadius: '0 3px 3px 0',
              backgroundColor: '#0284C7',
            },
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
        root: {
          '&:last-child td': { borderBottom: 0 },
          // La ligne survolée se teinte : sur un tableau large, l'œil ne perd
          // plus la ligne qu'il suit en allant chercher la dernière colonne.
          '&:hover > .MuiTableCell-body': { backgroundColor: '#F7FBFE' },
          '& > .MuiTableCell-body': { transition: 'background-color .12s ease' },
        },
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

    MuiLinearProgress: {
      styleOverrides: { root: { borderRadius: 4, height: 6 } },
    },

    MuiAvatar: {
      styleOverrides: { root: { fontWeight: 600 } },
    },

    MuiTab: {
      styleOverrides: {
        root: { textTransform: 'none', fontWeight: 600, minHeight: 44 },
      },
    },

    MuiIconButton: {
      styleOverrides: {
        root: { transition: 'background-color .15s ease, color .15s ease' },
      },
    },

    MuiDialog: {
      styleOverrides: {
        paper: {
          borderRadius: 16,
          // Sur un telephone, un dialogue centre laisse des marges perdues et
          // rend les formulaires longs illisibles. Il prend donc tout l'ecran,
          // comme une page a part entiere.
          '@media (max-width:600px)': {
            margin: 0,
            width: '100%',
            maxWidth: '100%',
            height: '100%',
            maxHeight: '100%',
            borderRadius: 0,
          },
        },
      },
    },

    MuiTableCell: {
      styleOverrides: {
        // Une cellule qui se coupe en trois lignes rend un tableau illisible :
        // mieux vaut faire defiler horizontalement, ce que TableContainer
        // permet deja. Les marges se resserrent sur petit ecran.
        root: {
          whiteSpace: 'nowrap',
          '@media (max-width:600px)': { padding: '8px 10px' },
        },
      },
    },

    MuiTableContainer: {
      styleOverrides: { root: { overflowX: 'auto' } },
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
