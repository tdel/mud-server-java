# mud-server-java

Portage Java/Spring Boot d'un serveur MUD (initialement en PHP/Swoole), avec des règles de
jeu inspirées de DnD5e : combat au tour par tour, personnages avec caractéristiques, classes
et compétences maîtrisées, déplacement sur grille hexagonale, objets/équipement, monstres
avec butin et zones d'agressivité, PNJ marchands. Le protocole de jeu est du JSON pur sur
socket TCP brute (voir ci-dessous).

## Commandes

Une commande par ligne, groupées par état de connexion — voir `network/ConnectionState`
(`CONNECTED`, `CHARSELECT`, `INGAME`) et les classes `CommandHandler` sous
`network/command/{connected,charselect,ingame}`.

**Non connecté** (`CONNECTED`)

| Commande | Effet |
| --- | --- |
| `register` | Créer un compte |
| `login` | Se connecter à un compte existant |
| `quit` | Fermer la connexion |

**Sélection de personnage** (`CHARSELECT`, juste après le login ; la liste des personnages
du compte, s'il y en a, est affichée automatiquement en entrant)

| Commande | Effet |
| --- | --- |
| `character-list` | Lister les personnages du compte |
| `character-create` | Créer un personnage (nom, genre, race, classe) |
| `character-select` | Incarner un personnage existant (`character-select <name>`) |
| `character-delete` | Supprimer un personnage (`character-delete <name>`) |

**En jeu** (`INGAME`)

| Commande | Effet |
| --- | --- |
| `look` | Décrire la room et la grille hexagonale courantes |
| `examine` | Examiner un objet, un PNJ ou un monstre (`examine <uuid>`) |
| `go` | Se déplacer de 1 à N cases dans une direction (`go <direction> [nombre]`) |
| `stop` | Interrompre un déplacement en cours |
| `portal` | Emprunter le portail présent sur la case courante |
| `say` | Parler aux autres joueurs de la room |
| `talk` | Engager le dialogue avec un PNJ (peut ouvrir sa boutique) (`talk <uuid>`) |
| `drop` | Détruire définitivement un objet de l'inventaire (`drop <uuid>`) |
| `equip` / `unequip` | Équiper / retirer un objet (`equip <uuid>` / `unequip <uuid>`) |
| `inventory` | Lister son inventaire et son équipement |
| `use` | Utiliser un objet consommable (`use <uuid>`) |
| `stats` | Afficher sa fiche de personnage |
| `roll` | Lancer un dé |
| `check` | Faire un jet de compétence ou de sauvegarde contre une difficulté |
| `select` | Choisir sa cible de combat (`select <uuid>`) |
| `attack` | Attaquer sa cible sélectionnée (`attack [uuid]`) |
| `rest` | Repos court ou long, hors combat (`rest <short|long>`) |
| `save` | Sauvegarder l'état du personnage |

**Dans tout état authentifié** (`CHARSELECT`/`INGAME`)

| Commande | Effet |
| --- | --- |
| `logout` | Se déconnecter et revenir à l'écran de connexion |

**Dans tout état** (y compris avant login)

| Commande | Effet |
| --- | --- |
| `help` | Lister les commandes disponibles dans l'état courant |

## Protocole (JSON)

Le serveur expose un transport JSON pur sur socket TCP brute, port **4002**. Une ligne = un
message JSON, terminé par `\n`.

**Entrée (client → serveur)** — une commande :

```json
{"verb": "look", "argument": ""}
{"verb": "go", "argument": "nord 2"}
```

`verb` est traité insensible à la casse. Quand le serveur attend une réponse ponctuelle
(ex. confirmation), la ligne suivante est interprétée comme une réponse et non comme une
commande :

```json
{"reply": "yes"}
```

**Sortie (serveur → client)** — chaque message est enveloppé ainsi :

```json
{"type": "<NomDeLaClasse>", "payload": { ... }, "secure": false}
```

`type` est le nom simple de la classe Java du message (ex. `Chat`, `XpGained`,
`Inventory`) ; `secure` vaut `true` pour les messages sensibles (ex. mot de passe) à ne
pas logger/afficher en clair. La plupart des messages sérialisent directement leurs
champs comme `payload` :

```json
{"type": "XpGained", "payload": {"amount": 50}, "secure": false}
{"type": "Chat", "payload": {"speakerLogin": "aldric", "text": "salut"}, "secure": false}
```

Quatre messages construisent un `payload` dédié plutôt que de sérialiser l'objet
domaine brut :

- `ViewAround` — le viewport dynamique autour du joueur, rejoué à chaque déplacement :
  `mapName`, `mapDescription`, `cells` (liste de `{q, r, kind}`, `kind` ∈
  `self/floor/path/destination/portal/portalDestination/player/monster/npc/outOfBounds`),
  `portals` (`{direction, targetMapName}`), `charactersNearby`, `monstersNearby`,
  `npcsNearby`.
- `MapView` — la carte statique complète de la map (terrain + portails), envoyée une fois à
  l'entrée dans la map : `mapId`, `mapName`, `cells` (liste de `{q, r, walkable}`),
  `portals` (`{q, r, direction, targetMapName}`).
- `GamePlayerStats` — fiche de personnage : `name`, `gender`, `level`,
  `characterClass`, `currentHealth`, `maxHealth`, `armorClass`, `proficiencyBonus`,
  les six caractéristiques (`strength`, `dexterity`, ... en `{score, modifier}`),
  `primaryAbility`, `savingThrowProficiencies`, `skillProficiencies`.
- `MonsterStatBlock` — fiche de monstre : `name`, `description`, `currentHealth`,
  `maxHealth`, `armorClass`, les six caractéristiques en `{score, modifier}`.

En cas d'erreur de parsing ou d'exécution, le serveur répond avec `{"type": "Error",
"payload": {"message": "..."}, "secure": false}` sans fermer la connexion.

## Stack

- **Java 25**, **Spring Boot 4.1**
- **jOOQ** pour la persistance — un DSL SQL type-safe, pas un ORM : pas de contexte de
  persistance, pas de lazy loading, pas de dirty-checking
- **Flyway** comme source de vérité du schéma ; les classes jOOQ sont générées à la
  compilation directement depuis les migrations Flyway (aucune connexion DB requise pour la
  génération)
- **Netty** pour le transport TCP/JSON, avec un **virtual thread par connexion** (JDK 25)
  plutôt qu'un pool de threads partagé
- **SQLite** — fichier unique local, aucun serveur de base de données à faire tourner

## Lancer le projet

Prérequis : Java 25 (Temurin recommandé) et Maven installés sur la machine hôte — par exemple
via [SDKMAN](https://sdkman.io/) (`sdk install java 25-tem`, `sdk install maven`). La base
SQLite est un simple fichier local, rien d'autre à démarrer.

```bash
# Build
mvn package

# Tests
mvn test

# Lancer le serveur (crée/ouvre ./mud-server.db à la racine du projet)
mvn spring-boot:run
```

Le serveur écoute sur le port **4002** (protocole JSON, voir ci-dessus). Pour s'y connecter
en ligne de commande :

```bash
nc localhost 4002
```

## Pour aller plus loin

Conventions du projet, architecture de concurrence (virtual threads, verrouillage), et
détails par sous-système : voir [`CLAUDE.md`](CLAUDE.md).
