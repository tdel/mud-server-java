# mud-server-java

Portage Java/Spring Boot d'un serveur MUD telnet (initialement en PHP/Swoole), avec des
règles de jeu inspirées de DnD5e : combat au tour par tour, personnages avec caractéristiques
et classes, objets/équipement, monstres.

## Démo

![Démo d'une session telnet : connexion, déplacement et combat](docs/demo/demo.svg)

Connexion, sélection d'un personnage, déplacement dans le monde, combat contre un monstre —
une vraie session telnet enregistrée contre le serveur (voir [`docs/demo/`](docs/demo/) pour
régénérer cette démo).

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
