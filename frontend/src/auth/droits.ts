import type { Role } from '../api/types';

/**
 * Miroir de la matrice appliquee par le backend (voir Autorisations.java).
 * Ici on masque simplement ce qui serait refuse : c'est un confort d'usage,
 * la securite reste garantie cote serveur.
 */

const ENCADREMENT: Role[] = ['ADMIN', 'RESPONSABLE_COMMERCIAL'];
const LIRE_COMMERCIAL: Role[] = [
  'ADMIN',
  'RESPONSABLE_COMMERCIAL',
  'MAGASINIER',
  'COMMERCIAL',
  'COMPTABLE',
];
const ECRIRE_COMMERCIAL: Role[] = ['ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL'];
const FACTURATION: Role[] = ['ADMIN', 'COMPTABLE'];
const CATALOGUE: Role[] = ['ADMIN', 'RESPONSABLE_IMPORT'];
const STOCK: Role[] = ['ADMIN', 'MAGASINIER'];
/** Tout le monde sauf le comptable, qui n'a rien a y faire. */
const VOIR_STOCK: Role[] = [
  'ADMIN',
  'RESPONSABLE_COMMERCIAL',
  'MAGASINIER',
  'COMMERCIAL',
  'RESPONSABLE_IMPORT',
];

const a = (role: Role | undefined, roles: Role[]) => !!role && roles.includes(role);

export const droits = (role: Role | undefined) => ({
  /** Consulter clients, devis, commandes et factures. */
  lireCommercial: a(role, LIRE_COMMERCIAL),
  /** Creer et modifier clients, devis et commandes. */
  ecrireCommercial: a(role, ECRIRE_COMMERCIAL),
  /** Valider les remises, fixer les plafonds, bloquer un client. */
  encadrer: a(role, ENCADREMENT),
  /** Emettre, envoyer et encaisser les factures. */
  ecrireFacture: a(role, FACTURATION),
  /** Mouvements de stock. */
  ecrireStock: a(role, STOCK),
  /** Ecran de stock : le comptable facture, il ne suit pas l'entrepot. */
  voirStock: a(role, VOIR_STOCK),
  /**
   * Valider une commande (choix des depots), la preparer, la livrer : metier de
   * l'entrepot. Le responsable commercial en est exclu, il pilote la vente.
   */
  traiterCommande: a(role, STOCK),
  /** Annuler une commande : decision commerciale, pas logistique. */
  annulerCommande: a(role, ECRIRE_COMMERCIAL),
  /** Produits et marques. */
  ecrireCatalogue: a(role, CATALOGUE),
  /** Categories : structure du catalogue. */
  ecrireCategorie: role === 'ADMIN',
  /** Parametres de l'application, dont les seuils de remise. */
  administrer: role === 'ADMIN',
  /** Fournisseurs. */
  voirFournisseurs: a(role, CATALOGUE),
  /**
   * Le commercial ne fixe ni prix ni TVA : le backend impose les conditions du
   * catalogue, les champs sont donc affiches en lecture seule.
   */
  prixImposes: role === 'COMMERCIAL',
});

export type Droits = ReturnType<typeof droits>;
