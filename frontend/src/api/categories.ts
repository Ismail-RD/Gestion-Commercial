import { api } from './client';
import type { Categorie, CategorieRequest } from './types';

export async function listerCategories(): Promise<Categorie[]> {
  const { data } = await api.get<Categorie[]>('/api/categories');
  return data;
}

export async function creerCategorie(payload: CategorieRequest): Promise<Categorie> {
  const { data } = await api.post<Categorie>('/api/categories', payload);
  return data;
}

export async function modifierCategorie(id: number, payload: CategorieRequest): Promise<Categorie> {
  const { data } = await api.put<Categorie>(`/api/categories/${id}`, payload);
  return data;
}

export async function supprimerCategorie(id: number): Promise<void> {
  await api.delete(`/api/categories/${id}`);
}
