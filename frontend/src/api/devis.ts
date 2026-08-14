import { api } from './client';
import type { Devis, DevisRequest, PageResponse, StatutDevis } from './types';

export interface DevisQuery {
  recherche?: string;
  numero?: string;
  statut?: StatutDevis;
  clientId?: number;
  commercialId?: number;
  dateMin?: string;
  dateMax?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export async function listerDevis(query: DevisQuery): Promise<PageResponse<Devis>> {
  const { data } = await api.get<PageResponse<Devis>>('/api/devis', { params: query });
  return data;
}

export async function getDevis(id: number): Promise<Devis> {
  const { data } = await api.get<Devis>(`/api/devis/${id}`);
  return data;
}

export async function creerDevis(payload: DevisRequest): Promise<Devis> {
  const { data } = await api.post<Devis>('/api/devis', payload);
  return data;
}

export async function modifierDevis(id: number, payload: DevisRequest): Promise<Devis> {
  const { data } = await api.put<Devis>(`/api/devis/${id}`, payload);
  return data;
}

export async function supprimerDevis(id: number): Promise<void> {
  await api.delete(`/api/devis/${id}`);
}

export async function envoyerDevis(id: number): Promise<Devis> {
  const { data } = await api.post<Devis>(`/api/devis/${id}/envoyer`);
  return data;
}

export async function accepterDevis(id: number, commentaire?: string): Promise<Devis> {
  const { data } = await api.post<Devis>(`/api/devis/${id}/accepter`, { commentaireClient: commentaire });
  return data;
}

export async function refuserDevis(id: number, commentaire?: string): Promise<Devis> {
  const { data } = await api.post<Devis>(`/api/devis/${id}/refuser`, { commentaireClient: commentaire });
  return data;
}

/** Valide un devis en attente (remise > seuil) : passe a ENVOYE. Reserve a l'admin. */
export async function validerRemiseDevis(id: number): Promise<Devis> {
  const { data } = await api.post<Devis>(`/api/devis/${id}/valider-remise`);
  return data;
}

/** Refuse la remise d'un devis en attente : retour a BROUILLON. Reserve a l'admin. */
export async function refuserRemiseDevis(id: number): Promise<Devis> {
  const { data } = await api.post<Devis>(`/api/devis/${id}/refuser-remise`);
  return data;
}

/**
 * Envoie le devis au client par email (PDF joint + lien personnel).
 * N'altere pas le statut du devis : la validation reste manuelle.
 */
export async function envoyerDevisParEmail(id: number): Promise<void> {
  await api.post(`/api/devis/${id}/envoyer-email`);
}

/** Bon de commande depose par le client, a telecharger pour verification. */
export async function telechargerBonCommande(id: number): Promise<Blob> {
  const { data } = await api.get(`/api/devis/${id}/bon-commande`, { responseType: 'blob' });
  return data;
}

/** Recupere le PDF du devis (le JWT est injecte par l'intercepteur). */
export async function telechargerDevisPdf(id: number): Promise<Blob> {
  const { data } = await api.get(`/api/devis/${id}/pdf`, { responseType: 'blob' });
  return data;
}
