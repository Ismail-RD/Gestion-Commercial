import { api } from './client';
import type { PageResponse } from './types';

export type StatutCommandeFournisseur =
  | 'BROUILLON'
  | 'COMMANDEE'
  | 'EN_TRANSIT'
  | 'EN_DOUANE'
  | 'RECEPTIONNEE_PARTIELLEMENT'
  | 'RECEPTIONNEE'
  | 'ANNULEE';

export type Incoterm = 'EXW' | 'FOB' | 'CFR' | 'CIF' | 'DAP' | 'DDP';
export type ModeTransport = 'MARITIME' | 'AERIEN' | 'ROUTIER';

export interface LigneCommandeFournisseur {
  id: number;
  produitId: number;
  reference: string;
  referenceFournisseur: string | null;
  designation: string;
  quantiteCommandee: number;
  quantiteRecue: number | null;
  prixUnitaireDevise: number | null;
  montantDevise: number | null;
  quotePartFrais: number | null;
  coutUnitaireMAD: number | null;
}

export interface CommandeFournisseur {
  id: number;
  numero: string;
  statut: StatutCommandeFournisseur;
  fournisseurId: number;
  fournisseurNom: string;
  depotReceptionCode: string;
  acheteurNom: string | null;
  dateCreation: string;
  dateCommande: string | null;
  dateArriveePrevue: string | null;
  dateTransit: string | null;
  dateDouane: string | null;
  datePremiereReception: string | null;
  dateReception: string | null;
  dateAnnulation: string | null;
  devise: string | null;
  tauxChange: number | null;
  incoterm: Incoterm | null;
  modeTransport: ModeTransport | null;
  paysOrigine: string | null;
  fraisTransportEnDevise: boolean;
  transporteur: string | null;
  referenceTransport: string | null;
  portArrivee: string | null;
  montantDevise: number | null;
  montantMAD: number | null;
  fraisFret: number | null;
  fraisAssurance: number | null;
  droitsDouane: number | null;
  fraisTransit: number | null;
  totalFrais: number | null;
  coutTotalMAD: number | null;
  observations: string | null;
  lignes: LigneCommandeFournisseur[];
}

export interface CommandeFournisseurRequest {
  fournisseurId: number;
  depotReceptionCode: string;
  dateArriveePrevue?: string | null;
  devise?: string | null;
  tauxChange?: number | null;
  incoterm?: Incoterm | null;
  modeTransport?: ModeTransport | null;
  paysOrigine?: string | null;
  fraisTransportEnDevise?: boolean;
  dateCommande?: string | null;
  transporteur?: string | null;
  referenceTransport?: string | null;
  portArrivee?: string | null;
  fraisFret?: number | null;
  fraisAssurance?: number | null;
  droitsDouane?: number | null;
  fraisTransit?: number | null;
  observations?: string | null;
  lignes: {
    produitId: number;
    quantiteCommandee: number;
    prixUnitaireDevise?: number | null;
    referenceFournisseur?: string | null;
  }[];
}

export async function listerCommandesFournisseur(
  page = 0,
  size = 20,
): Promise<PageResponse<CommandeFournisseur>> {
  const { data } = await api.get<PageResponse<CommandeFournisseur>>(
    '/api/commandes-fournisseur',
    { params: { page, size, sort: 'dateCreation,desc' } },
  );
  return data;
}

export async function getCommandeFournisseur(id: number): Promise<CommandeFournisseur> {
  const { data } = await api.get<CommandeFournisseur>(`/api/commandes-fournisseur/${id}`);
  return data;
}

export async function creerCommandeFournisseur(
  payload: CommandeFournisseurRequest,
): Promise<CommandeFournisseur> {
  const { data } = await api.post<CommandeFournisseur>('/api/commandes-fournisseur', payload);
  return data;
}

export async function modifierCommandeFournisseur(
  id: number,
  payload: CommandeFournisseurRequest,
): Promise<CommandeFournisseur> {
  const { data } = await api.put<CommandeFournisseur>(
    `/api/commandes-fournisseur/${id}`, payload);
  return data;
}

export async function emettreCommandeFournisseur(id: number): Promise<CommandeFournisseur> {
  const { data } = await api.post<CommandeFournisseur>(
    `/api/commandes-fournisseur/${id}/emettre`);
  return data;
}

export async function changerStatutCommandeFournisseur(
  id: number,
  statut: StatutCommandeFournisseur,
): Promise<CommandeFournisseur> {
  const { data } = await api.patch<CommandeFournisseur>(
    `/api/commandes-fournisseur/${id}/statut`, null, { params: { statut } });
  return data;
}

/** Lignes omises = recues en totalite. */
export async function receptionnerCommandeFournisseur(
  id: number,
  lignes: { ligneId: number; quantiteRecue: number }[],
): Promise<CommandeFournisseur> {
  const { data } = await api.post<CommandeFournisseur>(
    `/api/commandes-fournisseur/${id}/receptionner`, { lignes });
  return data;
}

export async function annulerCommandeFournisseur(id: number): Promise<CommandeFournisseur> {
  const { data } = await api.post<CommandeFournisseur>(
    `/api/commandes-fournisseur/${id}/annuler`);
  return data;
}

export async function supprimerCommandeFournisseur(id: number): Promise<void> {
  await api.delete(`/api/commandes-fournisseur/${id}`);
}
