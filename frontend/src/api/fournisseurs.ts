import { api } from './client';
import type { Fournisseur, FournisseurRequest, PageResponse, TypeFournisseur } from './types';

export interface FournisseurQuery {
  recherche?: string;
  nom?: string;
  email?: string;
  typeFournisseur?: TypeFournisseur;
  page?: number;
  size?: number;
  sort?: string;
}

export async function listerFournisseurs(query: FournisseurQuery): Promise<PageResponse<Fournisseur>> {
  const { data } = await api.get<PageResponse<Fournisseur>>('/api/fournisseurs', { params: query });
  return data;
}

export async function trouverFournisseur(id: number): Promise<Fournisseur> {
  const { data } = await api.get<Fournisseur>(`/api/fournisseurs/${id}`);
  return data;
}

export async function creerFournisseur(payload: FournisseurRequest): Promise<Fournisseur> {
  const { data } = await api.post<Fournisseur>('/api/fournisseurs', payload);
  return data;
}

export async function modifierFournisseur(id: number, payload: FournisseurRequest): Promise<Fournisseur> {
  const { data } = await api.put<Fournisseur>(`/api/fournisseurs/${id}`, payload);
  return data;
}

export async function supprimerFournisseur(id: number): Promise<void> {
  await api.delete(`/api/fournisseurs/${id}`);
}
