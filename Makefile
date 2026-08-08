MVN_IMAGE := maven:3.9.16-eclipse-temurin-25
MVN_RUN := docker run --rm -v "$(CURDIR)":/app -w /app \
	-v /var/run/docker.sock:/var/run/docker.sock -v $(HOME)/.m2:/root/.m2 $(MVN_IMAGE)

.PHONY: help init start stop restart logs ps reset-db db-shell build package test run format format-check clean

help: ## Affiche cette aide
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "}; {printf "%-14s %s\n", $$1, $$2}'

init: reset-db package ## Première initialisation ou remise à zéro complète (DB + build)

start: ## Démarre les conteneurs Docker (Postgres dev)
	docker compose up -d

stop: ## Arrête les conteneurs Docker (sans supprimer les volumes)
	docker compose down

restart: stop start ## Redémarre les conteneurs Docker

logs: ## Suit les logs des conteneurs Docker
	docker compose logs -f

ps: ## Liste l'état des conteneurs Docker
	docker compose ps

reset-db: ## Repart d'une base de dev vide (supprime volumes + données)
	docker compose down -v
	docker run --rm -v "$(CURDIR)/docker/build/db":/data alpine rm -rf /data/postgres
	docker compose up -d db

db-shell: ## Ouvre un shell psql sur la base de dev
	docker compose exec db psql -U mud-server-java -d mud-server-java

build: package ## Alias de `package`

package: ## Build le projet (mvn package)
	$(MVN_RUN) mvn package

test: ## Lance la suite de tests (Testcontainers Postgres)
	$(MVN_RUN) mvn test

run: start ## Démarre le serveur telnet (mvn spring-boot:run)
	docker run --rm --network host -v "$(CURDIR)":/app -w /app \
		-v /var/run/docker.sock:/var/run/docker.sock -v $(HOME)/.m2:/root/.m2 $(MVN_IMAGE) mvn spring-boot:run

format: ## Formate le code (spotless:apply)
	$(MVN_RUN) mvn spotless:apply

format-check: ## Vérifie le formatage (spotless:check)
	$(MVN_RUN) mvn spotless:check

clean: ## Nettoie les artefacts de build (mvn clean)
	$(MVN_RUN) mvn clean
