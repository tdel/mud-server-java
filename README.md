# mud-server-java

Portage Java/Spring Boot d'un serveur MUD telnet (initialement en PHP/Swoole), avec des
règles de jeu inspirées de DnD5e : combat au tour par tour, personnages avec caractéristiques,
classes et compétences maîtrisées, déplacement sur grille hexagonale, objets/équipement,
monstres avec butin et zones d'agressivité, PNJ marchands.

## Démo

![Démo de deux sessions telnet en parallèle : deux joueurs qui discutent puis affrontent un monstre ensemble](docs/demo/demo.svg)

Deux joueurs connectés simultanément (Aldric et Elowen, chacun dans son panneau tmux),
déplacement sur la grille hexagonale, achat auprès d'un PNJ marchand, discussion entre les
deux via `say`, combat déclenché automatiquement à l'approche d'un monstre puis rejoint par
le second joueur en cours de route, et butin récupéré à sa mort — deux vraies sessions
telnet enregistrées côte à côte contre le serveur (voir [`docs/demo/`](docs/demo/) pour
régénérer cette démo).

## Commandes

Une commande par ligne, groupées par état de connexion — voir `network/ConnectionState`
et les classes `ControllerHandler` sous `controller/{connected,authed,ingame}`.

**Non connecté**

| Commande | Effet |
| --- | --- |
| `register` | Créer un compte |
| `login` | Se connecter à un compte existant |
| `quit` | Fermer la connexion |

**Connecté, avant sélection de personnage**

| Commande | Effet |
| --- | --- |
| `characters-list` | Lister les personnages du compte |
| `character-create` | Créer un personnage (nom, genre, race, classe) |
| `character-select` | Incarner un personnage existant |
| `character-delete` | Supprimer un personnage |

**En jeu**

| Commande | Effet |
| --- | --- |
| `look` | Décrire la room et la grille hexagonale courantes |
| `examine` | Examiner un objet, un PNJ ou un monstre |
| `go` | Se déplacer de 1 à N cases dans une direction (`go <direction> [nombre]`) |
| `say` | Parler aux autres joueurs de la room |
| `talk` | Engager le dialogue avec un PNJ (peut ouvrir sa boutique) |
| `take` / `drop` | Ramasser / déposer un objet au sol |
| `equip` / `unequip` | Équiper / retirer un objet |
| `inventory` | Lister son inventaire et son équipement |
| `use` | Utiliser un objet consommable |
| `stats` | Afficher sa fiche de personnage |
| `roll` | Lancer un dé |
| `check` | Faire un jet de compétence ou de sauvegarde contre une difficulté |
| `select` | Choisir sa cible de combat |
| `attack` | Attaquer sa cible sélectionnée |
| `save` | Sauvegarder l'état du personnage |

**Dans tout état authentifié**

| Commande | Effet |
| --- | --- |
| `logout` | Se déconnecter et revenir à l'écran de connexion |

## Stack

- **Java 25**, **Spring Boot 4.1**
- **jOOQ** pour la persistance — un DSL SQL type-safe, pas un ORM : pas de contexte de
  persistance, pas de lazy loading, pas de dirty-checking
- **Flyway** comme source de vérité du schéma ; les classes jOOQ sont générées à la
  compilation directement depuis les migrations Flyway (aucune connexion DB requise pour la
  génération)
- **Netty** pour le serveur telnet, avec un **virtual thread par connexion** (JDK 25) plutôt
  qu'un pool de threads partagé
- **PostgreSQL**

## Lancer le projet

Ni Java ni Maven ne sont nécessaires sur la machine hôte : tout passe par Docker.

```bash
# Build
docker run --rm -v "$(pwd)":/app -w /app -v /var/run/docker.sock:/var/run/docker.sock -v ~/.m2:/root/.m2 \
  maven:3.9.16-eclipse-temurin-25 mvn package

# Tests (lance son propre Postgres via Testcontainers — le socket Docker doit être monté)
docker run --rm -v "$(pwd)":/app -w /app -v /var/run/docker.sock:/var/run/docker.sock -v ~/.m2:/root/.m2 \
  maven:3.9.16-eclipse-temurin-25 mvn test

# Postgres de développement (persistant, séparé de celui des tests)
docker compose up -d db

# Lancer le serveur
docker run --rm -v "$(pwd)":/app -w /app -v /var/run/docker.sock:/var/run/docker.sock -v ~/.m2:/root/.m2 \
  maven:3.9.16-eclipse-temurin-25 mvn spring-boot:run
```

Le serveur telnet écoute sur le port **4001** ; le Postgres de développement (`docker
compose`) expose le port **5433** (mappé vers le 5432 du conteneur) — deux ports distincts,
à ne pas confondre.

```bash
telnet localhost 4001
```

## Pour aller plus loin

Conventions du projet, architecture de concurrence (virtual threads, verrouillage), et
détails par sous-système : voir [`CLAUDE.md`](CLAUDE.md).
