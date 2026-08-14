/**
 * Regles de validation partagees entre les formulaires.
 * Doivent rester alignees sur les contraintes backend (@Pattern + CHECK en base) :
 * le front donne un retour immediat, le back reste l'autorite.
 */

/** ICE marocain : exactement 15 chiffres. Vide = non renseigne (autorise). */
export function iceInvalide(ice: string | undefined | null): boolean {
  if (!ice || !ice.trim()) {
    return false;
  }
  return !/^\d{15}$/.test(ice.trim());
}

export const MESSAGE_ICE = "L'ICE doit contenir exactement 15 chiffres";

/** Identifiant fiscal marocain : exactement 8 chiffres. Vide = non renseigne (autorise). */
export function identifiantFiscalInvalide(valeur: string | undefined | null): boolean {
  if (!valeur || !valeur.trim()) {
    return false;
  }
  return !/^\d{8}$/.test(valeur.trim());
}

export const MESSAGE_IDENTIFIANT_FISCAL = "L'identifiant fiscal doit contenir exactement 8 chiffres";
