import { Box, Stack, Typography } from '@mui/material';

/**
 * Suivi des dates d'un document : les étapes reellement franchies, dans
 * l'ordre ou elles se sont produites.
 *
 * <p>Un seul composant pour les quatre documents, parce que le reproche fait a
 * l'affichage des paiements etait justement l'inverse : des dates jetees les
 * unes a la suite des autres, dans un ordre qui variait d'un écran a l'autre.
 * Ici la regle est unique · une étape non franchie ne s'affiche pas, une étape
 * franchie s'affiche toujours au même endroit et dans le même format.
 */
export interface Etape {
  libelle: string;
  /** Date ISO. L'etape est ignoree si elle est absente. */
  date?: string | null;
  /** Precision facultative : montant, quantite, motif. */
  detail?: string | null;
  /** Etape annoncee et non encore survenue (echeance, arrivee prevue). */
  prevu?: boolean;
}

/** Formate en tenant compte de la présence ou non d'une heure. */
function formatEtape(date: string): string {
  const d = new Date(date);
  const avecHeure = date.includes('T');
  return avecHeure
    ? `${d.toLocaleDateString('fr-FR')} à ${d.toLocaleTimeString('fr-FR', {
        hour: '2-digit', minute: '2-digit' })}`
    : d.toLocaleDateString('fr-FR');
}

export default function SuiviDates({
  etapes,
  titre,
  dense = false,
}: {
  etapes: Etape[];
  titre?: string;
  /** Version compacte, pour un suivi imbrique dans une ligne de tableau. */
  dense?: boolean;
}) {
  // L'ordre affiche est celui du cycle de vie, tel que l'appelant declare ses
  // étapes -- pas un tri sur les dates. Deux raisons. Les étapes n'ont pas
  // toutes une heure : une date seule vaut minuit et passerait avant une étape
  // horodatee du même jour, ce qui montrait une facturé soldée avant d'être
  // émise. Et une date saisie a la main peut être incoherente (un chèque
  // "emis" après avoir été recu) : trier dessus deplacerait l'étape au lieu de
  // laisser voir l'anomalie.
  const franchies = etapes.filter((e) => e.date && !e.prevu);
  // Ce qui est annonce sans être survenu ferme la liste : c'est du calendrier,
  // pas encore de l'histoire.
  const aVenir = etapes.filter((e) => e.date && e.prevu);
  const visibles = [...franchies, ...aVenir];

  if (visibles.length === 0) {
    return null;
  }

  return (
    <Box>
      {titre && (
        <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
          {titre}
        </Typography>
      )}
      <Stack spacing={dense ? 0.25 : 1}>
        {visibles.map((e) => (
          <Stack
            key={e.libelle}
            direction="row"
            spacing={dense ? 1 : 2}
            sx={{ alignItems: 'baseline' }}
          >
            <Typography
              variant={dense ? 'caption' : 'body2'}
              sx={{
                minWidth: dense ? 88 : 150,
                fontVariantNumeric: 'tabular-nums',
                color: e.prevu ? 'text.disabled' : 'text.secondary',
              }}
            >
              {formatEtape(e.date!)}
            </Typography>
            <Typography
              variant={dense ? 'caption' : 'body2'}
              sx={{
                fontWeight: e.prevu ? 400 : 500,
                color: e.prevu ? 'text.disabled' : 'text.primary',
                // Un libellé coupe en deux lignes casse l'alignement des dates.
                whiteSpace: 'nowrap',
              }}
            >
              {e.libelle}
              {e.prevu && ' (prévu)'}
            </Typography>
            {e.detail && (
              <Typography variant="caption" color="text.secondary">
                {e.detail}
              </Typography>
            )}
          </Stack>
        ))}
      </Stack>
    </Box>
  );
}
