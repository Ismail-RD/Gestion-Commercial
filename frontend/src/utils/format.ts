/**
 * Formatage des montants de l'application.
 * Centralise ici pour qu'un changement de devise reste un changement d'un seul
 * endroit (l'euro etait auparavant code en dur dans chaque page).
 */
const DEVISE = 'DH';

/** Ex : 8500 -> "8 500,00 DH" */
export function formatMontant(montant: number | null | undefined): string {
  return `${formatNombre(montant)} ${DEVISE}`;
}

/**
 * Montant sans devise. Necessaire des qu'une autre monnaie est en jeu : les
 * achats a l'import se libellent en euros ou en dollars, et accoler "DH" a un
 * montant en devise dirait le contraire de la verite.
 */
export function formatNombre(montant: number | null | undefined): string {
  if (montant === null || montant === undefined) {
    return '0,00';
  }
  return new Intl.NumberFormat('fr-MA', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(montant);
}
