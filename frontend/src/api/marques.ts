import { api } from './client';
import type { Marque, MarqueRequest, PageResponse } from './types';

export interface MarqueQuery {
  nom?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export async function listerMarques(query: MarqueQuery): Promise<PageResponse<Marque>> {
  const { data } = await api.get<PageResponse<Marque>>('/api/marques', { params: query });
  return data;
}

export async function listerToutesMarques(): Promise<Marque[]> {
  const { data } = await api.get<Marque[]>('/api/marques/all');
  return data;
}

export async function creerMarque(payload: MarqueRequest): Promise<Marque> {
  const { data } = await api.post<Marque>('/api/marques', payload);
  return data;
}

export async function modifierMarque(id: number, payload: MarqueRequest): Promise<Marque> {
  const { data } = await api.put<Marque>(`/api/marques/${id}`, payload);
  return data;
}

export async function supprimerMarque(id: number): Promise<void> {
  await api.delete(`/api/marques/${id}`);
}
