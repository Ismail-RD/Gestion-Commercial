import { api } from './client';
import type { Facture, FactureRequest, PageResponse, StatutFacture } from './types';

export interface FactureQuery {
  recherche?: string;
  numero?: string;
  statut?: StatutFacture;
  clientId?: number;
  dateMin?: string;
  dateMax?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export async function listerFactures(query: FactureQuery): Promise<PageResponse<Facture>> {
  const { data } = await api.get<PageResponse<Facture>>('/api/factures', { params: query });
  return data;
}

export async function getFacture(id: number): Promise<Facture> {
  const { data } = await api.get<Facture>(`/api/factures/${id}`);
  return data;
}

export async function creerFacture(payload: FactureRequest): Promise<Facture> {
  const { data } = await api.post<Facture>('/api/factures', payload);
  return data;
}

/**
 * Met a jour l'echeance d'une facture : montants et lignes decoulent de la
 * commande facturee et ne se modifient pas ici.
 */
export async function modifierFacture(id: number, dateEcheance: string): Promise<Facture> {
  const { data } = await api.put<Facture>(`/api/factures/${id}`, { dateEcheance });
  return data;
}

/** Recupere le PDF de la facture (le JWT est injecte par l'intercepteur). */
export async function telechargerFacturePdf(id: number): Promise<Blob> {
  const { data } = await api.get(`/api/factures/${id}/pdf`, { responseType: 'blob' });
  return data;
}

/** Envoie la facture au client par email, PDF en piece jointe. */
export async function envoyerFactureParEmail(id: number): Promise<void> {
  await api.post(`/api/factures/${id}/envoyer-email`);
}

export async function supprimerFacture(id: number): Promise<void> {
  await api.delete(`/api/factures/${id}`);
}
