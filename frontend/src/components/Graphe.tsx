import { useId, useState } from 'react';
import { Box, Stack, Typography } from '@mui/material';
import { BLEU_MARQUE, ORANGE_MARQUE, VERT_MARQUE } from '../theme';

/**
 * Les trois lectures graphiques du tableau de bord, dessinées en SVG.
 *
 * <p>Pas de bibliothèque de graphiques : trois formes suffisent ici, et une
 * dépendance de plus coûterait davantage en poids et en montées de version
 * qu'elle ne ferait gagner. Le SVG donne en prime la maîtrise complète des
 * couleurs, ce qui compte quand la charte vient d'un logo.
 *
 * <p>Le serveur fournit déjà la part de chaque valeur, rapportée à la plus
 * grande. L'écran ne calcule donc rien : il dessine.
 */

export type FormeVisuel = 'SERIE' | 'CLASSEMENT' | 'REPARTITION';

export interface Barre {
  libelle: string;
  valeur: string;
  detail?: string | null;
  /** Longueur relative, de 0 à 100. */
  part: number;
}

/** Palette de la répartition : les couleurs du pictogramme, puis des dérivés. */
const PALETTE = [BLEU_MARQUE, VERT_MARQUE, ORANGE_MARQUE, '#7C5CFF', '#0284C7', '#E0B400'];

export default function Graphe({
  forme,
  barres,
}: {
  forme: FormeVisuel;
  barres: Barre[];
}) {
  if (barres.length === 0) {
    return null;
  }
  if (forme === 'SERIE') {
    return <Serie barres={barres} />;
  }
  if (forme === 'REPARTITION') {
    return <Repartition barres={barres} />;
  }
  return <Classement barres={barres} />;
}

/* ------------------------------------------------------------------ Série */

/**
 * Courbe d'aire : sur une suite de mois, c'est la pente qui informe, pas la
 * hauteur de chaque colonne prise isolément.
 */
function Serie({ barres }: { barres: Barre[] }) {
  const id = useId();
  const [survole, setSurvole] = useState<number | null>(null);

  const largeur = 800;
  const hauteur = 220;
  const margeBas = 28;
  const margeHaut = 12;
  const utile = hauteur - margeBas - margeHaut;

  const pas = barres.length > 1 ? largeur / (barres.length - 1) : largeur;
  const points = barres.map((b, i) => ({
    x: barres.length > 1 ? i * pas : largeur / 2,
    y: margeHaut + utile - (Math.max(b.part, 0) / 100) * utile,
    b,
  }));

  const ligne = points.map((p) => `${p.x},${p.y}`).join(' ');
  const aire = `${points[0].x},${hauteur - margeBas} ${ligne} `
    + `${points[points.length - 1].x},${hauteur - margeBas}`;

  return (
    <Box sx={{ position: 'relative' }}>
      <Box
        component="svg"
        viewBox={`0 0 ${largeur} ${hauteur}`}
        preserveAspectRatio="none"
        sx={{ width: '100%', height: 220, display: 'block', overflow: 'visible' }}
      >
        <defs>
          <linearGradient id={`aire-${id}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={BLEU_MARQUE} stopOpacity="0.35" />
            <stop offset="100%" stopColor={BLEU_MARQUE} stopOpacity="0" />
          </linearGradient>
        </defs>

        {/* Trois repères horizontaux : assez pour situer, trop peu pour gêner. */}
        {[0, 0.5, 1].map((r) => (
          <line
            key={r}
            x1="0" x2={largeur}
            y1={margeHaut + utile * r} y2={margeHaut + utile * r}
            stroke="#E3ECF3" strokeWidth="1"
          />
        ))}

        <polygon points={aire} fill={`url(#aire-${id})`} />
        <polyline
          points={ligne}
          fill="none"
          stroke={BLEU_MARQUE}
          strokeWidth="2.5"
          strokeLinejoin="round"
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />

        {points.map((p, i) => (
          <g key={p.b.libelle}>
            <circle
              cx={p.x} cy={p.y} r={survole === i ? 6 : 4}
              fill="#FFFFFF" stroke={BLEU_MARQUE} strokeWidth="2.5"
            />
            {/* Bande invisible : la souris n'a pas à viser le point. */}
            <rect
              x={p.x - pas / 2} y={0} width={pas} height={hauteur}
              fill="transparent"
              onMouseEnter={() => setSurvole(i)}
              onMouseLeave={() => setSurvole(null)}
            />
          </g>
        ))}
      </Box>

      {/* Les libellés hors du SVG : ils gardent ainsi la typographie du site. */}
      <Stack direction="row" sx={{ justifyContent: 'space-between', mt: -2 }}>
        {barres.map((b, i) => (
          <Typography
            key={b.libelle}
            variant="caption"
            sx={{
              flex: 1, textAlign: 'center',
              color: survole === i ? 'text.primary' : 'text.disabled',
              fontWeight: survole === i ? 600 : 400,
            }}
          >
            {b.libelle}
          </Typography>
        ))}
      </Stack>

      <Box sx={{ minHeight: 24, mt: 0.5, textAlign: 'center' }}>
        {survole !== null && (
          <Typography variant="body2">
            <strong>{barres[survole].valeur}</strong>
            <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
              {barres[survole].libelle}
            </Typography>
          </Typography>
        )}
      </Box>
    </Box>
  );
}

/* ------------------------------------------------------------- Classement */

/**
 * Barres horizontales : le libellé se lit à l'endroit, quelle que soit sa
 * longueur · ce qu'une colonne verticale ne permet pas.
 */
function Classement({ barres }: { barres: Barre[] }) {
  const id = useId();
  return (
    <Stack spacing={2}>
      {barres.map((b, i) => (
        <Box key={b.libelle}>
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'baseline', mb: 0.5 }}>
            <Typography variant="body2" sx={{ fontWeight: 500 }}>
              {b.libelle}
              {b.detail && (
                <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                  {b.detail}
                </Typography>
              )}
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 700 }}>{b.valeur}</Typography>
          </Stack>
          <Box
            component="svg"
            viewBox="0 0 100 6"
            preserveAspectRatio="none"
            sx={{ width: '100%', height: 10, display: 'block' }}
          >
            <defs>
              <linearGradient id={`barre-${id}-${i}`} x1="0" y1="0" x2="1" y2="0">
                <stop offset="0%" stopColor={BLEU_MARQUE} />
                <stop offset="100%" stopColor="#0284C7" />
              </linearGradient>
            </defs>
            <rect x="0" y="0" width="100" height="6" rx="3" fill="#EDF4F9" />
            <rect
              x="0" y="0" width={Math.max(b.part, 1.5)} height="6" rx="3"
              fill={`url(#barre-${id}-${i})`}
            />
          </Box>
        </Box>
      ))}
    </Stack>
  );
}

/* ------------------------------------------------------------ Répartition */

/**
 * Anneau : la part de chacun dans le total se saisit d'un coup d'œil, alors
 * qu'une suite de barres oblige à faire l'addition de tête.
 */
function Repartition({ barres }: { barres: Barre[] }) {
  const total = barres.reduce((somme, b) => somme + Math.max(b.part, 0), 0);
  if (total <= 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        Rien à répartir pour le moment.
      </Typography>
    );
  }

  const rayon = 54;
  const epaisseur = 22;
  const circonference = 2 * Math.PI * rayon;
  let parcouru = 0;

  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={4} sx={{ alignItems: 'center' }}>
      <Box component="svg" viewBox="0 0 140 140" sx={{ width: 160, height: 160, flexShrink: 0 }}>
        <g transform="translate(70,70) rotate(-90)">
          <circle r={rayon} fill="none" stroke="#EDF4F9" strokeWidth={epaisseur} />
          {barres.map((b, i) => {
            const part = Math.max(b.part, 0) / total;
            const longueur = part * circonference;
            const cercle = (
              <circle
                key={b.libelle}
                r={rayon}
                fill="none"
                stroke={PALETTE[i % PALETTE.length]}
                strokeWidth={epaisseur}
                strokeDasharray={`${longueur} ${circonference - longueur}`}
                strokeDashoffset={-parcouru}
              />
            );
            parcouru += longueur;
            return cercle;
          })}
        </g>
      </Box>

      <Stack spacing={1.2} sx={{ flexGrow: 1, width: '100%' }}>
        {barres.map((b, i) => (
          <Stack key={b.libelle} direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
            <Box sx={{
              width: 12, height: 12, borderRadius: '3px', flexShrink: 0,
              bgcolor: PALETTE[i % PALETTE.length],
            }} />
            <Typography variant="body2" sx={{ flexGrow: 1 }}>{b.libelle}</Typography>
            <Typography variant="body2" sx={{ fontWeight: 700 }}>{b.valeur}</Typography>
          </Stack>
        ))}
      </Stack>
    </Stack>
  );
}
