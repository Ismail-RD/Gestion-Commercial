import axios from 'axios';
import { accentuer } from '../utils/francais';

const TOKEN_KEY = 'gc_token';
const USER_KEY = 'gc_user';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
});

/**
 * Le token et l'utilisateur forment une seule session : ils doivent toujours
 * etre poses et effaces ensemble. Les dissocier laisse un etat "a moitie
 * connecte" ou l'appli croit l'utilisateur authentifie alors que ses appels
 * partent sans token.
 */
export const sessionStorageGc = {
  getToken: () => localStorage.getItem(TOKEN_KEY),
  getUser: () => localStorage.getItem(USER_KEY),
  save: (token: string, user: unknown) => {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  },
  clear: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },
};

// Injecte le token JWT sur chaque requete
api.interceptors.request.use((config) => {
  const token = sessionStorageGc.getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Sur 401 : session invalide/expiree -> on efface TOUTE la session et on
// renvoie vers le login (sinon l'utilisateur reste bloque dans un etat incoherent).
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      sessionStorageGc.clear();
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    // Les messages d'erreur sont rediges cote serveur, sans accents. On les
    // retablit ici plutot que dans chacune des pages qui les affichent : c'est
    // le seul point par lequel ils passent tous.
    const message = error.response?.data?.message;
    if (typeof message === 'string') {
      error.response.data.message = accentuer(message);
    }
    return Promise.reject(error);
  },
);
