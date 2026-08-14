import { api } from './client';
import type { Role, Utilisateur } from './types';

/** Liste les utilisateurs, filtrable par role (alimente les listes deroulantes). */
export async function listerUtilisateurs(role?: Role): Promise<Utilisateur[]> {
  const { data } = await api.get<Utilisateur[]>('/api/utilisateurs', {
    params: role ? { role } : undefined,
  });
  return data;
}
