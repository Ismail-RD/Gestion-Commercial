/**
 * Traduction des énumérations de l'API en français lisible.
 *
 * <p>Le serveur transporte des constantes — `BLOQUE`, `VIREMENT`, `ENTREE` —
 * parce que ce sont des valeurs, pas du texte. Les afficher telles quelles
 * donne un écran qui parle la langue de la base plutôt que celle du métier.
 * La traduction vit donc ici, au plus près de l'affichage, et une seule fois :
 * le même statut se retrouve sur la liste, sur la fiche et dans les filtres.
 *
 * <p>`libelle` retombe sur la valeur brute si elle est inconnue : une nouvelle
 * constante ajoutée côté serveur s'affichera sans élégance, mais s'affichera.
 */

export const STATUT_CLIENT: Record<string, string> = {
  ACTIF: 'Actif',
  BLOQUE: 'Bloqué',
};

export const TYPE_TIERS: Record<string, string> = {
  ENTREPRISE: 'Entreprise',
  PARTICULIER: 'Particulier',
};

export const ROLE: Record<string, string> = {
  ADMIN: 'Administrateur',
  RESPONSABLE_COMMERCIAL: 'Responsable commercial',
  COMMERCIAL: 'Commercial',
  MAGASINIER: 'Magasinier',
  COMPTABLE: 'Comptable',
  RESPONSABLE_IMPORT: 'Responsable import',
};

export const MODE_PAIEMENT: Record<string, string> = {
  ESPECES: 'Espèces',
  CHEQUE: 'Chèque',
  VIREMENT: 'Virement',
  CARTE: 'Carte',
  TRAITE: 'Traite',
};

export const TYPE_MOUVEMENT: Record<string, string> = {
  ENTREE: 'Entrée',
  SORTIE: 'Sortie',
  AJUSTEMENT: 'Ajustement',
};

export const MODE_TRANSPORT: Record<string, string> = {
  MARITIME: 'Maritime',
  AERIEN: 'Aérien',
  ROUTIER: 'Routier',
};

/** Statuts de tous les documents, réunis : une fiche client les mélange. */
export const STATUT_DOCUMENT: Record<string, string> = {
  // Devis
  BROUILLON: 'Brouillon',
  EN_ATTENTE_VALIDATION: 'Remise à valider',
  ENVOYE: 'Envoyé',
  ACCEPTE: 'Accepté',
  REFUSE: 'Refusé',
  EXPIRE: 'Expiré',
  // Commande
  EN_ATTENTE: 'En attente',
  VALIDEE: 'Validée',
  EN_PREPARATION: 'En préparation',
  LIVREE: 'Livrée',
  ANNULEE: 'Annulée',
  // Facture
  EMISE: 'Émise',
  PARTIELLEMENT_PAYEE: 'Partiellement payée',
  PAYEE: 'Payée',
  EN_RETARD: 'En retard',
  // Commande fournisseur
  COMMANDEE: 'Commandée',
  EN_TRANSIT: 'En transit',
  EN_DOUANE: 'En douane',
  RECEPTIONNEE_PARTIELLEMENT: 'Reçue partiellement',
  RECEPTIONNEE: 'Réceptionnée',
  // Paiement
  RECU: 'Reçu',
  DEPOSE: 'Remis en banque',
  ENCAISSE: 'Encaissé',
  REJETE: 'Rejeté',
};

/** Cherche le libellé, et rend la valeur brute plutôt que rien si elle manque. */
export function libelle(table: Record<string, string>, valeur: string | null | undefined) {
  if (!valeur) {
    return '';
  }
  return table[valeur] ?? valeur;
}
