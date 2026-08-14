import { api } from './client';
import type { Role, Utilisateur } from './types';

export interface InvitationRequest {
  nom: string;
  prenom: string;
  email: string;
  role: Role;
}

/** Identite de l'invite, affichee sur la page ou il choisit son mot de passe. */
export interface Invitation {
  nom: string;
  prenom: string;
  email: string;
  role: Role;
}

export async function inviterUtilisateur(payload: InvitationRequest): Promise<Utilisateur> {
  const { data } = await api.post<Utilisateur>('/api/utilisateurs', payload);
  return data;
}

export async function renvoyerInvitation(id: number): Promise<Utilisateur> {
  const { data } = await api.post<Utilisateur>(`/api/utilisateurs/${id}/renvoyer-invitation`);
  return data;
}

/** Identite et role. Le mot de passe reste l'affaire de son titulaire. */
export async function modifierUtilisateur(
  id: number,
  payload: InvitationRequest,
): Promise<Utilisateur> {
  const { data } = await api.put<Utilisateur>(`/api/utilisateurs/${id}`, payload);
  return data;
}

/** Retire ou rend l'acces sans toucher a l'historique. */
export async function changerActivation(id: number, actif: boolean): Promise<Utilisateur> {
  const { data } = await api.patch<Utilisateur>(`/api/utilisateurs/${id}/activation`, { actif });
  return data;
}

export async function supprimerUtilisateur(id: number): Promise<void> {
  await api.delete(`/api/utilisateurs/${id}`);
}

/** Routes ouvertes : l'invite n'a pas encore de compte utilisable. */
export async function consulterInvitation(token: string): Promise<Invitation> {
  const { data } = await api.get<Invitation>(`/api/public/invitations/${token}`);
  return data;
}

export async function definirMotDePasse(token: string, motDePasse: string): Promise<void> {
  await api.post(`/api/public/invitations/${token}/mot-de-passe`, { motDePasse });
}
