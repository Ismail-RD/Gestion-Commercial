import { api } from './client';
import type {
  MouvementStock,
  PageResponse,
  StockAjustementRequest,
  StockApercu,
  StockEntreeRequest,
  StockProduit,
  StockSortieRequest,
  StockTransfertRequest,
  TypeMouvement,
} from './types';

export interface StockQuery {
  recherche?: string;
  produitId?: number;
  depotCode?: string;
  // true pour inclure les lignes a 0 (par defaut le stock nul est masque)
  inclureVides?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export interface MouvementQuery {
  recherche?: string;
  produitId?: number;
  depotCode?: string;
  type?: TypeMouvement;
  page?: number;
  size?: number;
  sort?: string;
}

export async function listerStock(query: StockQuery): Promise<PageResponse<StockProduit>> {
  const { data } = await api.get<PageResponse<StockProduit>>('/api/stock', { params: query });
  return data;
}

export async function listerMouvements(query: MouvementQuery): Promise<PageResponse<MouvementStock>> {
  const { data } = await api.get<PageResponse<MouvementStock>>('/api/stock/mouvements', { params: query });
  return data;
}

export interface ApercuQuery {
  recherche?: string;
  reference?: string;
  designation?: string;
  categorieId?: number;
  page?: number;
  size?: number;
  sort?: string;
}

/** Vue par produit : tout le catalogue avec le stock de chaque depot (0 inclus). */
export async function apercuStock(query: ApercuQuery): Promise<PageResponse<StockApercu>> {
  const { data } = await api.get<PageResponse<StockApercu>>('/api/stock/apercu', { params: query });
  return data;
}

export async function entreeStock(payload: StockEntreeRequest): Promise<StockProduit> {
  const { data } = await api.post<StockProduit>('/api/stock/entree', payload);
  return data;
}

export async function sortieStock(payload: StockSortieRequest): Promise<StockProduit> {
  const { data } = await api.post<StockProduit>('/api/stock/sortie', payload);
  return data;
}

export async function ajusterStock(payload: StockAjustementRequest): Promise<StockProduit> {
  const { data } = await api.post<StockProduit>('/api/stock/ajustement', payload);
  return data;
}

export async function transfertStock(payload: StockTransfertRequest): Promise<void> {
  await api.post('/api/stock/transfert', payload);
}
