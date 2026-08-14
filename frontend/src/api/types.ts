/**
 * Types de l'API, DERIVES de la spec OpenAPI du backend (src/api/schema.d.ts).
 *
 * Ne plus ecrire de type d'API a la main ici : un type ecrit a la main peut
 * mentir sur ce que l'API renvoie sans que TypeScript ne le voie (c'est ce qui
 * a produit les colonnes vides produitNom / depotNom / devisNumero).
 * Desormais, si un DTO change cote Spring, `npm run build` casse ici.
 *
 * Regenerer apres toute modification de l'API :  npm run gen:api
 */
import type { components } from './schema';

type Schemas = components['schemas'];

/**
 * Les DTO de reponse ne portent pas d'annotations de validation : springdoc
 * declare donc tous leurs champs optionnels. Jackson les serialise pourtant
 * systematiquement, y compris dans les objets imbriques (lignes, marques...) :
 * on retire donc l'optionalite en profondeur pour eviter des `?.` partout.
 *
 * Attention : cela dit "toujours present", pas "jamais null" · les `?? '-'`
 * du code restent utiles pour les valeurs nullables (commercialNom...).
 */
type Reponse<T> = T extends (infer U)[]
  ? Reponse<U>[]
  : T extends object
    ? { [K in keyof T]-?: Reponse<T[K]> }
    : T;

// --- Authentification ---
export type Role = NonNullable<Schemas['AuthResponse']['role']>;
export type AuthResponse = Reponse<Schemas['AuthResponse']>;
export type LoginRequest = Schemas['LoginRequest'];
export type Utilisateur = Reponse<Schemas['UtilisateurResponse']>;

// --- Pagination ---
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

// --- Produits ---
export type Produit = Reponse<Schemas['ProduitResponse']>;
export type ProduitRequest = Schemas['ProduitRequest'];
export type ProduitFournisseur = Reponse<Schemas['ProduitFournisseurResponse']>;
export type ProduitFournisseurRequest = Schemas['ProduitFournisseurRequest'];

// --- Categories ---
export type Categorie = Reponse<Schemas['CategorieResponse']>;
export type CategorieRequest = Schemas['CategorieRequest'];

// --- Marques ---
export type Marque = Reponse<Schemas['MarqueResponse']>;
export type MarqueRequest = Schemas['MarqueRequest'];

// --- Fournisseurs ---
export type TypeFournisseur = NonNullable<Schemas['FournisseurResponse']['typeFournisseur']>;
export type Fournisseur = Reponse<Schemas['FournisseurResponse']>;
export type FournisseurRequest = Schemas['FournisseurRequest'];

// --- Clients ---
export type TypeClient = Schemas['ClientRequest']['typeClient'];
export type StatutClient = NonNullable<Schemas['ClientResponse']['statut']>;
export type Client = Reponse<Schemas['ClientResponse']>;
export type ClientRequest = Schemas['ClientRequest'];

// --- Tiers (commun client / fournisseur) ---
export type RibDto = Schemas['RibDto'];

// --- Depots ---
export type Depot = Reponse<Schemas['DepotResponse']>;

// --- Devis ---
export type StatutDevis = NonNullable<Schemas['DevisResponse']['statut']>;
export type Devis = Reponse<Schemas['DevisResponse']>;
export type DevisRequest = Schemas['DevisRequest'];
export type LigneDevis = Reponse<Schemas['LigneDevisResponse']>;
export type LigneDevisRequest = Schemas['LigneDevisRequest'];

// --- Commandes ---
export type StatutCommande = NonNullable<Schemas['CommandeResponse']['statut']>;
export type Commande = Reponse<Schemas['CommandeResponse']>;
export type LigneCommande = Reponse<Schemas['LigneDocumentResponse']>;

// --- Factures ---
export type StatutFacture = NonNullable<Schemas['FactureResponse']['statut']>;
export type Facture = Reponse<Schemas['FactureResponse']>;
export type FactureRequest = Schemas['FactureRequest'];
export type LigneFacture = Reponse<Schemas['LigneDocumentResponse']>;

// --- Paiements ---
export type ModePaiement = NonNullable<Schemas['PaiementResponse']['modePaiement']>;
export type Paiement = Reponse<Schemas['PaiementResponse']>;
export type PaiementRequest = Schemas['PaiementRequest'];

// --- Stock ---
export type TypeMouvement = NonNullable<Schemas['MouvementStockResponse']['type']>;
export type StockProduit = Reponse<Schemas['StockProduitResponse']>;
export type StockDepot = Reponse<Schemas['StockDepotResponse']>;
export type StockApercu = Reponse<Schemas['StockApercuResponse']>;
export type MouvementStock = Reponse<Schemas['MouvementStockResponse']>;
export type StockEntreeRequest = Schemas['MouvementRequest'];
export type StockSortieRequest = Schemas['MouvementRequest'];
export type StockAjustementRequest = Schemas['AjustementRequest'];
export type StockTransfertRequest = Schemas['TransfertRequest'];
