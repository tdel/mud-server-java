.PHONY: help build package test run format format-check clean

help: ## Affiche cette aide
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "}; {printf "%-14s %s\n", $$1, $$2}'

build: package ## Alias de `package`

package: ## Build le projet (mvn package)
	mvn package

test: ## Lance la suite de tests
	mvn test

run: ## Démarre le serveur telnet (mvn spring-boot:run)
	mvn spring-boot:run

format: ## Formate le code (spotless:apply)
	mvn spotless:apply

format-check: ## Vérifie le formatage (spotless:check)
	mvn spotless:check

clean: ## Nettoie les artefacts de build (mvn clean)
	mvn clean
