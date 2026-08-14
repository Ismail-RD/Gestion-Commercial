import { api } from './client';
import type { PageResponse, Produit, ProduitRequest } from './types';

export interface ProduitQuery {
  recherche?: string;
  reference?: string;
  designation?: string;
  categorieId?: number;
  prixMin?: number;
  prixMax?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export async function listerProduits(query: ProduitQuery): Promise<PageResponse<Produit>> {
  const { data } = await api.get<PageResponse<Produit>>('/api/produits', { params: query });
  return data;
}

export async function trouverProduit(id: number): Promise<Produit> {
  const { data } = await api.get<Produit>(`/api/produits/${id}`);
  return data;
}

export async function creerProduit(payload: ProduitRequest): Promise<Produit> {
  const { data } = await api.post<Produit>('/api/produits', payload);
  return data;
}

export async function modifierProduit(id: number, payload: ProduitRequest): Promise<Produit> {
  const { data } = await api.put<Produit>(`/api/produits/${id}`, payload);
  return data;
}

export async function supprimerProduit(id: number): Promise<void> {
  await api.delete(`/api/produits/${id}`);
}

// --- Fiche technique ---

/** Envoie (ou remplace) la fiche technique du produit (PDF, JPG ou PNG). */
export async function uploaderFicheTechnique(id: number, fichier: File): Promise<Produit> {
  const formData = new FormData();
  formData.append('fichier', fichier);
  const { data } = await api.post<Produit>(`/api/produits/${id}/fiche-technique`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
}

/** Recupere le contenu de la fiche technique (le JWT est injecte par l'intercepteur). */
export async function telechargerFicheTechnique(id: number): Promise<Blob> {
  const { data } = await api.get(`/api/produits/${id}/fiche-technique`, { responseType: 'blob' });
  return data;
}

export async function supprimerFicheTechnique(id: number): Promise<Produit> {
  const { data } = await api.delete<Produit>(`/api/produits/${id}/fiche-technique`);
  return data;
}
