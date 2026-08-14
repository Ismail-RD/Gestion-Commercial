import { api } from './client';
import type { Paiement, PaiementRequest } from './types';

export async function listerPaiements(factureId: number): Promise<Paiement[]> {
  const { data } = await api.get<Paiement[]>('/api/paiements', { params: { factureId } });
  return data;
}

export async function creerPaiement(payload: PaiementRequest): Promise<Paiement> {
  const { data } = await api.post<Paiement>('/api/paiements', payload);
  return data;
}

/** Portefeuille : cheques et traites recus, pas encore encaisses. */
export async function listerEffets(): Promise<Paiement[]> {
  const { data } = await api.get<Paiement[]>('/api/paiements/effets');
  return data;
}

export async function deposerEffet(id: number, dateRemise?: string): Promise<Paiement> {
  const { data } = await api.post<Paiement>(`/api/paiements/${id}/deposer`, null, {
    params: dateRemise ? { dateRemise } : undefined,
  });
  return data;
}

export async function encaisserEffet(id: number, dateEncaissement?: string): Promise<Paiement> {
  const { data } = await api.post<Paiement>(`/api/paiements/${id}/encaisser`, null, {
    params: dateEncaissement ? { dateEncaissement } : undefined,
  });
  return data;
}

export async function rejeterEffet(id: number, motif: string): Promise<Paiement> {
  const { data } = await api.post<Paiement>(`/api/paiements/${id}/rejeter`, { motif });
  return data;
}

/** Un encaissement ne s'efface pas : il faut le rejeter d'abord. */
export async function supprimerPaiement(id: number): Promise<void> {
  await api.delete(`/api/paiements/${id}`);
}
