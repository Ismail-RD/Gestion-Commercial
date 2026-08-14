import { api } from './client';
import type { PageResponse } from './types';
import { accentuer } from '../utils/francais';

export type TypeNotification =
  | 'REMISE_A_VALIDER'
  | 'REMISE_VALIDEE'
  | 'REMISE_REFUSEE'
  | 'COMMANDE_A_PREPARER'
  | 'COMMANDE_A_FACTURER'
  | 'DEVIS_ACCEPTE'
  | 'DEVIS_REFUSE'
  | 'PAIEMENT_REJETE'
  | 'CLIENT_BLOQUE';

export type NiveauNotification = 'INFORMATION' | 'ALERTE' | 'URGENT';

export type TypeDocumentLie =
  | 'DEVIS'
  | 'COMMANDE'
  | 'COMMANDE_FOURNISSEUR'
  | 'FACTURE'
  | 'CLIENT';

export interface Notification {
  id: number;
  type: TypeNotification;
  niveau: NiveauNotification;
  titre: string;
  message: string | null;
  typeDocument: TypeDocumentLie | null;
  documentId: number | null;
  dateCreation: string;
  dateLecture: string | null;
}

export async function listerNotifications(size = 15): Promise<PageResponse<Notification>> {
  const { data } = await api.get<PageResponse<Notification>>('/api/notifications', {
    params: { size },
  });
  // Titres et messages sont rediges cote serveur, sans accents : la couche
  // d'affichage les retablit.
  return {
    ...data,
    content: data.content.map((n) => ({
      ...n,
      titre: accentuer(n.titre),
      message: accentuer(n.message),
    })),
  };
}

/** Compteur de la cloche, sollicite en boucle : une seule valeur, pas de liste. */
export async function compterNonLues(): Promise<number> {
  const { data } = await api.get<number>('/api/notifications/non-lues');
  return data;
}

export async function marquerLue(id: number): Promise<Notification> {
  const { data } = await api.post<Notification>(`/api/notifications/${id}/lue`);
  return data;
}

export async function marquerToutLu(): Promise<void> {
  await api.post('/api/notifications/toutes-lues');
}

/** Page vers laquelle renvoie une notification, ou null si elle ne mene nulle part. */
export function lienDuDocument(n: Notification): string | null {
  if (!n.typeDocument) {
    return null;
  }
  const pages: Record<TypeDocumentLie, string> = {
    DEVIS: '/devis',
    COMMANDE: '/commandes',
    COMMANDE_FOURNISSEUR: '/commandes-fournisseur',
    FACTURE: '/factures',
    CLIENT: '/clients',
  };
  return pages[n.typeDocument];
}
