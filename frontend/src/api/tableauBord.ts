import { api } from './client';
import type { Role } from './types';
import type { FormeVisuel } from '../components/Graphe';
import { accentuer } from '../utils/francais';

/** Ton d'un chiffre ou d'une ligne : dicte la couleur, pas le sens. */
export type Ton = 'neutre' | 'succes' | 'attention' | 'alerte';

export interface TableauBord {
  role: Role;
  titre: string;
  sousTitre: string;
  indicateurs: { libelle: string; valeur: string; detail: string; ton: Ton }[];
  files: {
    titre: string;
    description: string;
    lien: string;
    total: number;
    elements: {
      titre: string;
      sousTitre: string;
      info: string;
      lien: string;
      ton: Ton;
    }[];
  }[];
  /** Lecture graphique du rôle : série mensuelle, classement ou répartition. */
  visuel: {
    titre: string;
    description: string;
    /** Le serveur choisit le graphique : lui seul sait ce que sont les libellés. */
    forme: FormeVisuel;
    /** part : longueur relative de 0 à 100, calculée par le serveur. */
    barres: { libelle: string; valeur: string; detail: string; part: number }[];
  } | null;
}

/**
 * Les libellés du tableau de bord sont rédigés côté serveur, sans accents :
 * on les rétablit à l'arrivée, une fois pour toutes, plutôt qu'à chaque
 * endroit qui les affiche.
 *
 * <p>Seuls les textes rédigés sont corrigés. Les titres d'éléments et les
 * libellés de barres portent des données — numéro de document, nom de client,
 * référence produit — et doivent rester tels qu'ils ont été saisis.
 */
export async function monTableauBord(): Promise<TableauBord> {
  const { data } = await api.get<TableauBord>('/api/tableau-de-bord');
  return {
    ...data,
    titre: accentuer(data.titre),
    sousTitre: accentuer(data.sousTitre),
    indicateurs: data.indicateurs.map((i) => ({
      ...i,
      libelle: accentuer(i.libelle),
      detail: accentuer(i.detail),
    })),
    files: data.files.map((f) => ({
      ...f,
      titre: accentuer(f.titre),
      description: accentuer(f.description),
      elements: f.elements.map((e) => ({
        ...e,
        sousTitre: accentuer(e.sousTitre),
        info: accentuer(e.info),
      })),
    })),
    visuel: data.visuel && {
      ...data.visuel,
      titre: accentuer(data.visuel.titre),
      description: accentuer(data.visuel.description),
      barres: data.visuel.barres.map((b) => ({ ...b, detail: accentuer(b.detail) })),
    },
  };
}

export interface TableauBordStock {
  jours: number;
  valeur: { totale: number; reservee: number; disponible: number };
  parDepot: {
    depotCode: string;
    valeur: number;
    quantite: number;
    quantiteReservee: number;
  }[];
  parCategorie: { categorie: string; valeur: number }[];
  compteurs: {
    references: number;
    referencesEnStock: number;
    jamaisEntrees: number;
    ruptures: number;
    toutReserve: number;
    dormants: number;
    depots: number;
  };
  ruptures: {
    produitId: number;
    reference: string;
    designation: string;
    quantite: number;
    quantiteReservee: number;
    disponible: number;
    toutReserve: boolean;
  }[];
  transferts: {
    produitId: number;
    reference: string;
    designation: string;
    depotDemandeur: string;
    depotFournisseur: string;
    disponibleChezFournisseur: number;
  }[];
  dormants: {
    produitId: number;
    reference: string;
    designation: string;
    quantite: number;
    valeurImmobilisee: number;
    joursDepuisDerniereSortie: number | null;
  }[];
  rotations: {
    produitId: number;
    reference: string;
    designation: string;
    quantiteSortie: number;
  }[];
  flux: {
    entrees: number;
    sorties: number;
    ajustements: number;
    nombreMouvements: number;
  };
  derniersAjustements: {
    date: string;
    reference: string;
    designation: string;
    depotCode: string;
    quantite: number;
    motif: string | null;
    utilisateur: string;
  }[];
}

export async function tableauBordStock(jours: number): Promise<TableauBordStock> {
  const { data } = await api.get<TableauBordStock>('/api/tableau-de-bord/stock', {
    params: { jours },
  });
  return data;
}
