import { api } from './client';
import type { Client, ClientRequest, PageResponse, TypeClient } from './types';

export interface ClientQuery {
  recherche?: string;
  nom?: string;
  email?: string;
  typeClient?: TypeClient;
  commercialId?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export async function listerClients(query: ClientQuery): Promise<PageResponse<Client>> {
  const { data } = await api.get<PageResponse<Client>>('/api/clients', { params: query });
  return data;
}

export async function trouverClient(id: number): Promise<Client> {
  const { data } = await api.get<Client>(`/api/clients/${id}`);
  return data;
}

export async function creerClient(payload: ClientRequest): Promise<Client> {
  const { data } = await api.post<Client>('/api/clients', payload);
  return data;
}

export async function modifierClient(id: number, payload: ClientRequest): Promise<Client> {
  const { data } = await api.put<Client>(`/api/clients/${id}`, payload);
  return data;
}

export async function supprimerClient(id: number): Promise<void> {
  await api.delete(`/api/clients/${id}`);
}

/** Reattribue le client a un autre commercial. Reserve a l'admin. */
export async function reattribuerClient(id: number, commercialId: number): Promise<Client> {
  const { data } = await api.patch<Client>(`/api/clients/${id}/commercial`, { commercialId });
  return data;
}

/**
 * Definit le plafond de credit d'un client, apres sa creation. Il n'existe pas
 * de credit illimite : 0 signifie aucun credit accorde. Reserve a l'admin.
 */
export async function definirPlafondClient(id: number, plafondCredit: number): Promise<Client> {
  const { data } = await api.post<Client>(`/api/clients/${id}/plafond`, { plafondCredit });
  return data;
}

/** Debloque un client dont l'encours avait depasse le plafond. Reserve a l'admin. */
export async function debloquerClient(id: number): Promise<Client> {
  const { data } = await api.post<Client>(`/api/clients/${id}/debloquer`);
  return data;
}

/** Bloque manuellement un client. Reserve a l'admin. */
export async function bloquerClient(id: number): Promise<Client> {
  const { data } = await api.post<Client>(`/api/clients/${id}/bloquer`);
  return data;
}
