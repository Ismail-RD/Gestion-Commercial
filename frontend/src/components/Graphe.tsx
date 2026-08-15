import { useId, useState } from 'react';
import { Box, Stack, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { RAMPE_ANCIENNETE } from '../theme';

/**
 * Les trois lectures graphiques du tableau de bord, dessinées en SVG.
 *
 * <p>Pas de bibliothèque de graphiques : trois formes suffisent ici, et une
 * dépendance de plus coûterait davantage en poids et en montées de version
 * qu'elle ne ferait gagner.
 *
 * <p>Le serveur fournit déjà la part de chaque valeur, rapportée à la plus
 * grande. L'écran ne calcule donc rien : il dessine.
 *
 * <p>Trois principes gouvernent ces dessins. Le trait reste fin et la grille
 * discrète, pour que la donnée soit la seule chose sombre à l'écran. Chaque
 * forme répond au survol, parce qu'un graphique qui ne dit pas ses chiffres
 * exacts oblige à les deviner à la règle. Enfin la couleur ne porte jamais
 * seule une information : un libellé l'accompagne toujours, ce dont dépend la
 * lecture des daltoniens comme celle d'une impression en noir et blanc.
 */

export type FormeVisuel = 'SERIE' | 'CLASSEMENT' | 'REPARTITION';

export interface Barre {
  libelle: string;
  valeur: string;
  detail?: string | null;
  /** Longueur relative, de 0 à 100. */
  part: number;
}

const BLEU = '#0284C7';
const GRILLE = '#E3ECF3';
const PISTE = '#EDF4F9';

/** Teinte d'une part, la dernière servant à tout ce qui dépasse l'échelle. */
function teinteDeLaPart(index: number): string {
  return RAMPE_ANCIENNETE[Math.min(index, RAMPE_ANCIENNETE.length - 1)];
}

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

/** Étiquette flottante du survol, commune aux trois formes. */
function Bulle({
  titre,
  valeur,
  detail,
  x,
  y,
}: {
  titre: string;
  valeur: string;
  detail?: string | null;
  /** Position en pourcentage de la largeur, et en pixels depuis le haut. */
  x: number;
  y: number;
}) {
  return (
    <Box
      sx={{
        position: 'absolute',
        left: `${x}%`,
        top: y,
        transform: 'translate(-50%, -100%)',
        pointerEvents: 'none',
        zIndex: 2,
        px: 1.25,
        py: 0.75,
        borderRadius: 2,
        bgcolor: '#0F2A3D',
        color: '#FFFFFF',
        boxShadow: '0 8px 20px rgba(15, 42, 61, 0.28)',
        whiteSpace: 'nowrap',
      }}
    >
      <Typography variant="caption" sx={{ display: 'block', opacity: 0.75 }}>
        {titre}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 700 }}>
        {valeur}
      </Typography>
      {detail && (
        <Typography variant="caption" sx={{ display: 'block', opacity: 0.75 }}>
          {detail}
        </Typography>
      )}
    </Box>
  );
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
      {survole !== null && (
        <Bulle
          titre={barres[survole].libelle}
          valeur={barres[survole].valeur}
          detail={barres[survole].detail}
          x={(points[survole].x / largeur) * 100}
          y={(points[survole].y / hauteur) * 220 - 12}
        />
      )}

      <Box
        component="svg"
        viewBox={`0 0 ${largeur} ${hauteur}`}
        preserveAspectRatio="none"
        sx={{ width: '100%', height: 220, display: 'block', overflow: 'visible' }}
      >
        <defs>
          <linearGradient id={`aire-${id}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={BLEU} stopOpacity="0.28" />
            <stop offset="100%" stopColor={BLEU} stopOpacity="0" />
          </linearGradient>
        </defs>

        {/* Trois repères horizontaux : assez pour situer, trop peu pour gêner. */}
        {[0, 0.5, 1].map((r) => (
          <line
            key={r}
            x1="0" x2={largeur}
            y1={margeHaut + utile * r} y2={margeHaut + utile * r}
            stroke={GRILLE} strokeWidth="1"
          />
        ))}

        {/* Le repère vertical du point survolé : l'œil relie la courbe au mois
            sans avoir à suivre une horizontale imaginaire. */}
        {survole !== null && (
          <line
            x1={points[survole].x} x2={points[survole].x}
            y1={margeHaut} y2={hauteur - margeBas}
            stroke={BLEU} strokeWidth="1" strokeDasharray="4 4"
            vectorEffect="non-scaling-stroke"
          />
        )}

        <polygon points={aire} fill={`url(#aire-${id})`} />
        <polyline
          points={ligne}
          fill="none"
          stroke={BLEU}
          strokeWidth="2"
          strokeLinejoin="round"
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />

        {points.map((p, i) => (
          <g key={p.b.libelle}>
            {/* Un anneau blanc détache le point de l'aire qu'il surplombe. */}
            <circle
              cx={p.x} cy={p.y} r={survole === i ? 6 : 4}
              fill="#FFFFFF" stroke={BLEU} strokeWidth="2.5"
              vectorEffect="non-scaling-stroke"
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
    </Box>
  );
}

/* ------------------------------------------------------------- Classement */

/**
 * Barres horizontales : le libellé se lit à l'endroit, quelle que soit sa
 * longueur, ce qu'une colonne verticale ne permet pas.
 *
 * <p>Une seule teinte pour toutes les barres : ce qui distingue les lignes ici
 * est leur longueur, pas leur identité. Les colorer différemment laisserait
 * croire à une famille par couleur.
 *
 * <p>Barres en HTML plutôt qu'en SVG : un rectangle SVG étiré en largeur
 * déforme ses coins arrondis en ovales, alors qu'une bordure CSS garde le rayon
 * demandé quelle que soit la largeur.
 */
function Classement({ barres }: { barres: Barre[] }) {
  const [survole, setSurvole] = useState<number | null>(null);

  return (
    <Stack spacing={2}>
      {barres.map((b, i) => (
        <Box
          key={b.libelle}
          onMouseEnter={() => setSurvole(i)}
          onMouseLeave={() => setSurvole(null)}
        >
          <Stack
            direction="row"
            sx={{ justifyContent: 'space-between', alignItems: 'baseline', mb: 0.5 }}
          >
            <Typography variant="body2" sx={{ fontWeight: 500 }}>
              {b.libelle}
              {b.detail && (
                <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                  {b.detail}
                </Typography>
              )}
            </Typography>
            {/* La valeur est écrite à côté de chaque barre : personne n'a à
                mesurer une longueur pour connaître un montant. */}
            <Typography variant="body2" sx={{ fontWeight: 700 }}>{b.valeur}</Typography>
          </Stack>

          <Box sx={{ height: 10, borderRadius: 5, bgcolor: PISTE, overflow: 'hidden' }}>
            <Box
              sx={{
                height: '100%',
                width: `${Math.min(Math.max(b.part, 1.5), 100)}%`,
                borderRadius: 5,
                backgroundImage: `linear-gradient(90deg, ${alpha(BLEU, 0.75)} 0%, ${BLEU} 100%)`,
                transition: 'filter .15s ease',
                filter: survole === i ? 'saturate(1.25)' : 'none',
              }}
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
 *
 * <p>Les parts arrivent ordonnées, de la plus saine à la plus critique · c'est
 * la balance âgée des impayés. Elles portent donc un dégradé et non des
 * couleurs indépendantes : ce qui se dégrade avec le temps doit se voir foncer,
 * pas changer de famille.
 */
function Repartition({ barres }: { barres: Barre[] }) {
  const [survole, setSurvole] = useState<number | null>(null);

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
  /** Coupure blanche entre deux parts : sans elle, deux teintes voisines se
      lisent comme un seul bloc, surtout imprimées. */
  const coupure = 2;
  let parcouru = 0;

  const parts = barres.map((b) => {
    const fraction = Math.max(b.part, 0) / total;
    const longueur = fraction * circonference;
    const trace = {
      longueur: Math.max(longueur - coupure, 0.5),
      depart: parcouru,
      pourcentage: Math.round(fraction * 100),
    };
    parcouru += longueur;
    return trace;
  });

  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={4} sx={{ alignItems: 'center' }}>
      <Box sx={{ position: 'relative', flexShrink: 0 }}>
        <Box component="svg" viewBox="0 0 140 140" sx={{ width: 168, height: 168 }}>
          <g transform="translate(70,70) rotate(-90)">
            <circle r={rayon} fill="none" stroke={PISTE} strokeWidth={epaisseur} />
            {barres.map((b, i) => (
              <circle
                key={b.libelle}
                r={rayon}
                fill="none"
                stroke={teinteDeLaPart(i)}
                strokeWidth={survole === i ? epaisseur + 4 : epaisseur}
                strokeDasharray={`${parts[i].longueur} ${circonference - parts[i].longueur}`}
                strokeDashoffset={-parts[i].depart}
                style={{ transition: 'stroke-width .15s ease' }}
                onMouseEnter={() => setSurvole(i)}
                onMouseLeave={() => setSurvole(null)}
              />
            ))}
          </g>
        </Box>

        {/* Le creux de l'anneau n'est pas perdu : il porte la part survolée. */}
        <Box
          sx={{
            position: 'absolute', inset: 0,
            display: 'grid', placeItems: 'center',
            pointerEvents: 'none', textAlign: 'center', px: 4,
          }}
        >
          {survole !== null ? (
            <Box>
              <Typography variant="h6" sx={{ lineHeight: 1.1 }}>
                {parts[survole].pourcentage} %
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {barres[survole].valeur}
              </Typography>
            </Box>
          ) : (
            <Typography variant="caption" color="text.secondary">
              {barres.length} postes
            </Typography>
          )}
        </Box>
      </Box>

      {/* La légende porte le libellé, la part et la valeur : la couleur ne
          transporte donc jamais seule l'information. */}
      <Stack spacing={1.2} sx={{ flexGrow: 1, width: '100%' }}>
        {barres.map((b, i) => (
          <Stack
            key={b.libelle}
            direction="row"
            spacing={1.5}
            sx={{
              alignItems: 'center', borderRadius: 1.5, px: 1, py: 0.4, mx: -1,
              cursor: 'default',
              bgcolor: survole === i ? 'action.hover' : 'transparent',
              transition: 'background-color .15s ease',
            }}
            onMouseEnter={() => setSurvole(i)}
            onMouseLeave={() => setSurvole(null)}
          >
            <Box sx={{
              width: 12, height: 12, borderRadius: '3px', flexShrink: 0,
              bgcolor: teinteDeLaPart(i),
            }} />
            <Typography variant="body2" sx={{ flexGrow: 1, minWidth: 0 }} noWrap>
              {b.libelle}
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0 }}>
              {parts[i].pourcentage} %
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 700, flexShrink: 0 }}>
              {b.valeur}
            </Typography>
          </Stack>
        ))}
      </Stack>
    </Stack>
  );
}
