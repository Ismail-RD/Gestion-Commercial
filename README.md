# Gestion Commerciale — SOGETHERM

Application de gestion commerciale pour une entreprise de génie climatique
(thermique et bâtiment) : catalogue, clients, devis, commandes, livraisons,
facturation, encaissement, stock multi-dépôts et achats à l'import.

## Ce que couvre l'application

**Le cycle de vente**, du devis à l'encaissement. Un devis part chez le client
par email, avec un lien personnel qui lui permet de répondre et de déposer son
bon de commande sans avoir de compte. Une fois accepté, il devient commande,
puis bon de livraison, puis facture.

**Le contrôle des engagements.** Chaque rôle dispose d'un seuil de remise et
d'un plafond de crédit, réglés par l'administrateur. Au-delà du seuil, le
document est gelé jusqu'à l'arbitrage de l'encadrement ; au-delà du plafond, le
client est bloqué et ne reçoit plus ni devis ni commande.

**Le stock**, réparti sur plusieurs dépôts, avec réservation à la validation
d'une commande et sortie réelle à la livraison — la marchandise promise reste
visible tant qu'elle n'est pas partie.

**Les achats à l'import**, du bon de commande fournisseur à l'entrée en stock,
avec calcul du coût de revient débarqué : la marchandise en devise, le fret,
l'assurance, les droits de douane et le transit se répartissent sur les lignes
au prorata de ce qui arrive réellement, y compris en plusieurs livraisons.

**L'encaissement**, y compris le cycle des effets : un chèque ou une traite est
reçu, remis en banque, puis encaissé — et seul l'encaissement solde la facture.
Un effet rejeté rend la facture à nouveau due.

**Le pilotage** : un tableau de bord par rôle, qui montre d'abord ce que la
personne a à faire, et des notifications adressées nominativement.

## Pile technique

| Côté | Technologies |
|---|---|
| Serveur | Java 21, Spring Boot, Spring Security (JWT), JPA/Hibernate, Flyway |
| Base | PostgreSQL 18 |
| Client | React, TypeScript, Vite, MUI, TanStack Query |
| Documents | Thymeleaf et openhtmltopdf pour les PDF, JavaMail pour les envois |

Les types TypeScript de l'API sont générés depuis la spécification OpenAPI
(`npm run gen:api`) : le client ne peut pas diverger du serveur sans que la
compilation le signale.

## Démarrer

### Prérequis

PostgreSQL 18, Java 21 et Node 20.

### Base de données

```bash
createdb gestioncommerciale
```

Flyway crée et fait évoluer le schéma au démarrage de l'application ; il n'y a
rien à exécuter à la main.

### Configuration

Les identifiants ne sont pas versionnés. Copiez le modèle et complétez-le :

```bash
cp backend/application-local.properties.exemple backend/application-local.properties
```

La base se configure par variables d'environnement (`DB_USERNAME`,
`DB_PASSWORD`), le reste dans le fichier local.

**Le secret de signature des jetons est obligatoire.** Il n'a pas de valeur par
défaut : une clé écrite dans le dépôt est une clé publique, et quiconque la lit
peut forger un jeton d'administrateur. L'application refuse de démarrer sans,
avec un message qui dit quoi faire. Pour en générer un :

```bash
openssl rand -base64 48
```

Pour travailler avec des données d'exemple — comptes de démonstration, clients
et produits — ajoutez `app.donnees-demo=true` dans le fichier local. C'est faux
par défaut, précisément pour qu'une mise en production n'ait rien à désactiver.

> **Attention** : renseigner les identifiants SMTP active l'envoi de **vrais
> emails aux adresses réelles des clients**. Laissez-les vides tant que vous
> n'en avez pas besoin.

### Lancer

```bash
cd backend && ./mvnw spring-boot:run
```

```bash
cd frontend && npm install && npm run dev
```

Le serveur écoute sur le port 8080, l'interface sur le 5173.

### Tests

```bash
cd backend && ./mvnw test
```

La suite tourne sur une base H2 en mémoire, indépendante de PostgreSQL.

## Mise en production

Trois variables suffisent, et aucune n'a de valeur par défaut utilisable telle
quelle :

```bash
DB_URL / DB_USERNAME / DB_PASSWORD   # la base
JWT_SECRET                           # la clé de signature, propre à l'environnement
CORS_ORIGINS                         # l'adresse réelle du frontend
```

Sans `CORS_ORIGINS`, le serveur refuse son propre site : la valeur par défaut
ne connaît que `localhost`.

**Le premier administrateur.** Sur une base vierge, aucun compte n'existe et
personne ne peut se connecter — c'est voulu. Définissez, le temps du premier
démarrage seulement :

```bash
APP_ADMIN_EMAIL=... APP_ADMIN_MOT_DE_PASSE=...
```

Le compte n'est créé que si la table des utilisateurs est **entièrement vide**,
donc ce mécanisme ne peut pas faire réapparaître un accès sur une base en
service. Retirez ensuite ces deux variables : elles ne servent plus à rien.

Les autres comptes se créent depuis l'écran Utilisateurs. L'invité reçoit un
lien par email et choisit lui-même son mot de passe, qui n'existe alors nulle
part ailleurs que dans sa tête — aucun mot de passe n'est jamais écrit ni dans
le code, ni dans la configuration.

**Ne jamais activer `app.donnees-demo` en production** : il crée des comptes
dont les mots de passe figurent dans ce dépôt.

## Organisation du dépôt

```
backend/    API REST, règles métier, migrations Flyway, génération des PDF
frontend/   Interface React
```

Côté serveur, les règles qui gouvernent le métier sont regroupées plutôt que
dispersées : la matrice des droits dans `security/Autorisations`, les seuils et
plafonds dans `service/PolitiquePouvoirs`, le statut d'une facture dans
l'entité elle-même — parce qu'il dépend de trois champs qui bougent à trois
endroits différents.
