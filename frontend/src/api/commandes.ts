import { api } from './client';
import type { Commande, PageResponse, StatutCommande } from './types';

export interface CommandeQuery {
  recherche?: string;
  numero?: string;
  statut?: StatutCommande;
  clientId?: number;
  devisId?: number;
  /** true : masque les commandes deja facturees (liste des facturables). */
  nonFacturee?: boolean;
  dateMin?: string;
  dateMax?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export async function listerCommandes(query: CommandeQuery): Promise<PageResponse<Commande>> {
  const { data } = await api.get<PageResponse<Commande>>('/api/commandes', { params: query });
  return data;
}

export async function getCommande(id: number): Promise<Commande> {
  const { data } = await api.get<Commande>(`/api/commandes/${id}`);
  return data;
}

export async function creerCommandeDepuisDevis(devisId: number): Promise<Commande> {
  const { data } = await api.post<Commande>(`/api/commandes/depuis-devis/${devisId}`);
  return data;
}

/** Commande saisie directement, sans devis prealable. */
export interface CommandeRequest {
  clientId: number;
  lignes: LigneCommandeModifiee[];
}

export async function creerCommande(payload: CommandeRequest): Promise<Commande> {
  const { data } = await api.post<Commande>('/api/commandes', payload);
  return data;
}

/** Met a jour le client et les lignes d'une commande. */
export async function modifierCommande(id: number, payload: CommandeRequest): Promise<Commande> {
  const { data } = await api.put<Commande>(`/api/commandes/${id}`, payload);
  return data;
}

export async function changerStatutCommande(id: number, statut: StatutCommande): Promise<Commande> {
  const { data } = await api.patch<Commande>(`/api/commandes/${id}/statut`, { statut });
  return data;
}

/** Aval de l'encadrement sur une remise excessive : la commande repart en EN_ATTENTE. */
export async function validerRemiseCommande(id: number): Promise<Commande> {
  const { data } = await api.post<Commande>(`/api/commandes/${id}/valider-remise`);
  return data;
}

/** Valide une commande : chaque ligne indique son depot de prelevement (decremente le stock). */
export async function validerCommande(
  id: number,
  lignes: { ligneId: number; depotCode: string }[],
): Promise<Commande> {
  const { data } = await api.post<Commande>(`/api/commandes/${id}/valider`, { lignes });
  return data;
}

/** Une ligne de la nouvelle composition de la commande. */
export interface LigneCommandeModifiee {
  produitId: number;
  quantite: number;
  /** Conditions negociees ; omises, celles du devis ou du catalogue s'appliquent. */
  prixUnitaire?: number;
  tauxTVA?: number;
  remise?: number;
  /** Requis uniquement pour un produit ajoute alors que la commande est validee. */
  depotCode?: string;
}

/**
 * Redefinit les lignes de la commande : la liste remplace l'existant.
 * Les produits ajoutes doivent figurer sur le devis d'origine.
 */
export async function modifierLignesCommande(
  id: number,
  lignes: LigneCommandeModifiee[],
): Promise<Commande> {
  const { data } = await api.put<Commande>(`/api/commandes/${id}/lignes`, { lignes });
  return data;
}

/** Bon de livraison PDF, avec ou sans les prix et les totaux. */
export async function telechargerBonLivraison(id: number, avecPrix: boolean): Promise<Blob> {
  const { data } = await api.get(`/api/commandes/${id}/bon-livraison`, {
    params: { avecPrix },
    responseType: 'blob',
  });
  return data;
}

/** Bon de preparation PDF (liste de picking, sans prix ni signature). */
export async function telechargerBonPreparation(id: number): Promise<Blob> {
  const { data } = await api.get(`/api/commandes/${id}/preparation`, { responseType: 'blob' });
  return data;
}

export async function supprimerCommande(id: number): Promise<void> {
  await api.delete(`/api/commandes/${id}`);
}
