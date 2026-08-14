/**
 * Rétablit les accents sur le texte qui arrive du serveur.
 *
 * <p>Les libellés du tableau de bord, les titres de notification et les
 * messages d'erreur sont rédigés côté serveur, dans un français sans accents.
 * Les corriger là-bas toucherait au code métier ; la correction vit donc ici,
 * dans la couche qui affiche — le serveur reste inchangé, et l'écran est écrit
 * correctement.
 *
 * <p>Deux précautions gouvernent ce fichier. D'abord, on ne remplace que des
 * <em>mots entiers</em> : sans cette frontière, « emise » à l'intérieur de
 * « Remises » donnerait « Rémises ». Ensuite, la fonction ne s'applique qu'aux
 * champs de texte rédigé, jamais aux données : un client nommé « Societe Bel »
 * doit garder son nom tel qu'il a été saisi.
 */

/** Mots entiers, corrigés partout où ils apparaissent isolés. */
const MOTS: Record<string, string> = {
  // Vocabulaire des documents
  reference: 'référence', references: 'références',
  Reference: 'Référence', References: 'Références',
  designation: 'désignation', Designation: 'Désignation',
  quantite: 'quantité', quantites: 'quantités',
  Quantite: 'Quantité', Quantites: 'Quantités',
  numero: 'numéro', Numero: 'Numéro',
  categorie: 'catégorie', categories: 'catégories',
  Categorie: 'Catégorie', Categories: 'Catégories',
  depot: 'dépôt', depots: 'dépôts', Depot: 'Dépôt', Depots: 'Dépôts',
  echeance: 'échéance', echeances: 'échéances',
  Echeance: 'Échéance', Echeances: 'Échéances',
  echue: 'échue', echues: 'échues', Echue: 'Échue', Echues: 'Échues',
  reglement: 'règlement', Reglement: 'Règlement',
  reglee: 'réglée', reglees: 'réglées',
  facture: 'facture', facturee: 'facturée', Facturee: 'Facturée',
  soldee: 'soldée', soldees: 'soldées', Soldee: 'Soldée',
  impaye: 'impayé', impayes: 'impayés', Impaye: 'Impayé',
  cheque: 'chèque', cheques: 'chèques', Cheque: 'Chèque',
  especes: 'espèces', Especes: 'Espèces',
  emise: 'émise', emises: 'émises', Emise: 'Émise', Emises: 'Émises',
  emis: 'émis', Emis: 'Émis',
  emission: 'émission', Emission: 'Émission',
  encaisse: 'encaissé', encaissee: 'encaissée', Encaisse: 'Encaissé',
  rejete: 'rejeté', rejetee: 'rejetée', Rejete: 'Rejeté',
  depose: 'déposé', deposee: 'déposée',
  receptionne: 'réceptionné', receptionnee: 'réceptionnée',
  Receptionne: 'Réceptionné', Receptionnee: 'Réceptionnée',
  reception: 'réception', Reception: 'Réception',
  livree: 'livrée', livrees: 'livrées', Livree: 'Livrée',
  preparation: 'préparation', Preparation: 'Préparation',
  prevue: 'prévue', prevu: 'prévu', Prevue: 'Prévue', Prevu: 'Prévu',
  arrivee: 'arrivée', Arrivee: 'Arrivée',
  entree: 'entrée', entrees: 'entrées', Entree: 'Entrée', Entrees: 'Entrées',
  annulee: 'annulée', annulees: 'annulées', Annulee: 'Annulée',
  validee: 'validée', validees: 'validées', Validee: 'Validée',
  refusee: 'refusée', Refusee: 'Refusée',
  acceptee: 'acceptée', acceptes: 'acceptés', Acceptee: 'Acceptée',
  envoye: 'envoyé', envoyee: 'envoyée', envoyes: 'envoyés',
  Envoye: 'Envoyé', Envoyee: 'Envoyée', Envoyes: 'Envoyés',
  creee: 'créée', cree: 'créé', Creee: 'Créée',
  creation: 'création', Creation: 'Création',
  supprimee: 'supprimée', supprime: 'supprimé',
  // « bloque » n'est pas dans cette liste : c'est aussi le verbe, et
  // « ce qui bloque » ne prend pas d'accent. Le participe est traite plus bas,
  // dans les locutions ou il ne peut pas etre confondu.
  bloquee: 'bloquée', bloques: 'bloqués', Bloques: 'Bloqués',
  debloque: 'débloqué', deblocage: 'déblocage', Deblocage: 'Déblocage',
  reserve: 'réservé', reservee: 'réservée', reservees: 'réservées',
  Reservees: 'Réservées', Reservee: 'Réservée',
  transferer: 'transférer', transfere: 'transféré',

  // Vocabulaire courant
  deja: 'déjà', Deja: 'Déjà',
  apres: 'après', Apres: 'Après',
  tres: 'très', Tres: 'Très',
  etat: 'état', etats: 'états', Etat: 'État',
  etape: 'étape', etapes: 'étapes', Etape: 'Étape',
  tache: 'tâche', taches: 'tâches', Tache: 'Tâche',
  meme: 'même', memes: 'mêmes', Meme: 'Même',
  etre: 'être', Etre: 'Être',
  ete: 'été',
  periode: 'période', Periode: 'Période',
  societe: 'société', Societe: 'Société',
  activite: 'activité', Activite: 'Activité',
  validite: 'validité', Validite: 'Validité',
  anciennete: 'ancienneté', Anciennete: 'Ancienneté',
  cout: 'coût', couts: 'coûts', Cout: 'Coût',
  credit: 'crédit', Credit: 'Crédit',
  detail: 'détail', details: 'détails', Detail: 'Détail',
  delai: 'délai', delais: 'délais', Delai: 'Délai', Delais: 'Délais',
  element: 'élément', elements: 'éléments', Element: 'Élément',
  operation: 'opération', operations: 'opérations',
  reponse: 'réponse', reponses: 'réponses', Reponse: 'Réponse',
  succes: 'succès', acces: 'accès', Acces: 'Accès',
  systeme: 'système', Systeme: 'Système',
  probleme: 'problème', Probleme: 'Problème',
  derniere: 'dernière', dernieres: 'dernières', Derniere: 'Dernière',
  premiere: 'première', premieres: 'premières', Premiere: 'Première',
  gele: 'gelé', gelee: 'gelée',
  autorise: 'autorisé', autorisee: 'autorisée',
  depasse: 'dépassé', depassee: 'dépassée', Depasse: 'Dépassé',
  depassement: 'dépassement',
  tresorerie: 'trésorerie', Tresorerie: 'Trésorerie',
  securite: 'sécurité', Securite: 'Sécurité',
  necessaire: 'nécessaire',
  telecharger: 'télécharger', Telecharger: 'Télécharger',
  generer: 'générer', generee: 'générée',
  role: 'rôle', roles: 'rôles', Role: 'Rôle', Roles: 'Rôles',
  controle: 'contrôle', Controle: 'Contrôle',
  repartis: 'répartis', repartition: 'répartition',
  recu: 'reçu', recue: 'reçue', recus: 'reçus', Recu: 'Reçu', Recue: 'Reçue',
  apercu: 'aperçu', Apercu: 'Aperçu',
  facon: 'façon',
  regle: 'réglé', regles: 'réglés',
  payee: 'payée', payees: 'payées', Payee: 'Payée',
  expire: 'expiré', expiree: 'expirée', Expire: 'Expiré',
};

/**
 * Locutions : le « a » préposition ne peut pas se corriger mot à mot, sans
 * quoi « il y a » deviendrait « il y à ». Seules ces tournures sont traitées.
 */
const LOCUTIONS: [string, string][] = [
  ['Au-dela', 'Au-delà'], ['au-dela', 'au-delà'],
  ['ou en est', 'où en est'], ['Ou en est', 'Où en est'],
  ['la ou ', 'là où '],
  ['Client bloque', 'Client bloqué'],
  ['client bloque', 'client bloqué'],
  ['est bloque', 'est bloqué'],
  ["d'ici la", "d'ici là"],
  ["n'a tranche", "n'a tranché"],
  ['a arbitrer', 'à arbitrer'],
  ['a valider', 'à valider'],
  ['a preparer', 'à préparer'],
  ['a facturer', 'à facturer'],
  ['a remettre', 'à remettre'],
  ['a receptionner', 'à réceptionner'],
  ['a relancer', 'à relancer'],
  ['a traiter', 'à traiter'],
  ['a confirmer', 'à confirmer'],
  ['a suivre', 'à suivre'],
  ['a encaisser', 'à encaisser'],
  ['a envoyer', 'à envoyer'],
  ['a commander', 'à commander'],
  ['a transferer', 'à transférer'],
  ['a livrer', 'à livrer'],
  ['a jour', 'à jour'],
  ['a la livraison', 'à la livraison'],
  ['a la commande', 'à la commande'],
  ['a la reception', 'à la réception'],
  ['a mes clients', 'à mes clients'],
  ['a ce jour', 'à ce jour'],
  ['a partir de', 'à partir de'],
  ['revient a ', 'revient à '],
  ['reste du', 'reste dû'],
  ['Reste du', 'Reste dû'],
  ['Chiffre facture', 'Chiffre facturé'],
  ['chiffre facture', 'chiffre facturé'],
  ['Facture ce mois', 'Facturé ce mois'],
  ['Montant facture', 'Montant facturé'],
];

const MOTIF = new RegExp(`\\b(${Object.keys(MOTS).join('|')})\\b`, 'g');

/**
 * Corrige un texte rédigé côté serveur. À réserver aux libellés et aux
 * messages : appliqué à un nom de client ou à une désignation de produit, il
 * modifierait une donnée saisie par l'utilisateur.
 */
export function accentuer(texte: string): string;
export function accentuer(texte: string | null): string | null;
export function accentuer(texte: string | undefined): string | undefined;
export function accentuer(texte: string | null | undefined) {
  if (!texte) {
    return texte;
  }
  let resultat = texte;
  for (const [source, cible] of LOCUTIONS) {
    if (resultat.includes(source)) {
      resultat = resultat.split(source).join(cible);
    }
  }
  return resultat.replace(MOTIF, (mot) => MOTS[mot] ?? mot);
}
