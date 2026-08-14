import axios from 'axios';

/**
 * Espace client : consulte le devis via le jeton du lien recu par email.
 * Volontairement sur une instance axios distincte de `api` : aucune session
 * n'existe ici, et un 401 ne doit pas rediriger le client vers /login.
 */
const publicApi = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
});

export interface DevisPublic {
  numero: string;
  reference?: string | null;
  date?: string | null;
  dateValidite?: string | null;
  montantHT?: number | null;
  montantTTC?: number | null;
  clientNom?: string | null;
  societeNom?: string | null;
  reponseClient?: 'ACCEPTE' | 'REFUSE' | null;
  dateReponseClient?: string | null;
  bonCommandeDepose: boolean;
}

export async function getDevisPublic(token: string): Promise<DevisPublic> {
  const { data } = await publicApi.get<DevisPublic>(`/api/public/devis/${token}`);
  return data;
}

export async function telechargerDevisPublicPdf(token: string): Promise<Blob> {
  const { data } = await publicApi.get(`/api/public/devis/${token}/pdf`, {
    responseType: 'blob',
  });
  return data;
}

/** Acceptation : le bon de commande signe est obligatoire. */
export async function accepterDevisPublic(token: string, bonCommande: File): Promise<void> {
  const form = new FormData();
  form.append('bonCommande', bonCommande);
  await publicApi.post(`/api/public/devis/${token}/accepter`, form);
}

export async function refuserDevisPublic(token: string, commentaire?: string): Promise<void> {
  await publicApi.post(`/api/public/devis/${token}/refuser`, { commentaire });
}
