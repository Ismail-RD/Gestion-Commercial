import { api } from './client';
import type { Depot } from './types';

export async function listerDepots(): Promise<Depot[]> {
  const { data } = await api.get<Depot[]>('/api/depots');
  return data;
}

export async function creerDepot(code: string): Promise<Depot> {
  const { data } = await api.post<Depot>('/api/depots', { code });
  return data;
}

export async function modifierDepot(id: number, code: string): Promise<Depot> {
  const { data } = await api.put<Depot>(`/api/depots/${id}`, { code });
  return data;
}

export async function supprimerDepot(id: number): Promise<void> {
  await api.delete(`/api/depots/${id}`);
}
