import { api } from './client';
import type { Role } from './types';

/** Ce qu'un role engage seul : remise consentie et credit accorde. */
export interface PouvoirRole {
  role: Role;
  seuilRemisePct: number;
  /** Null pour un role qui ne fixe pas les plafonds de credit. */
  plafondCreditMax: number | null;
}

export async function listerPouvoirs(): Promise<PouvoirRole[]> {
  const { data } = await api.get<PouvoirRole[]>('/api/parametres/pouvoirs');
  return data;
}

export async function modifierPouvoir(
  role: Role,
  pouvoir: { seuilRemisePct: number; plafondCreditMax: number | null },
): Promise<PouvoirRole> {
  const { data } = await api.put<PouvoirRole>(`/api/parametres/pouvoirs/${role}`, pouvoir);
  return data;
}
