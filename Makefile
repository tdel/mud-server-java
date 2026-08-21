MVN_IMAGE := maven:3.9.16-eclipse-temurin-25
MVN_RUN := docker run --rm -v "$(CURDIR)":/app -w /app -v $(HOME)/.m2:/root/.m2 $(MVN_IMAGE)

.PHONY: help build package test run format format-check clean

help: ## Affiche cette aide
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "}; {printf "%-14s %s\n", $$1, $$2}'

build: package ## Alias de `package`

package: ## Build le projet (mvn package)
	$(MVN_RUN) mvn package

test: ## Lance la suite de tests
	$(MVN_RUN) mvn test

run: ## Démarre le serveur telnet (mvn spring-boot:run)
	docker run --rm -p 4001:4001 -p 4002:4002 -p 8081:8081 \
		-v "$(CURDIR)":/app -w /app -v $(HOME)/.m2:/root/.m2 $(MVN_IMAGE) mvn spring-boot:run

format: ## Formate le code (spotless:apply)
	$(MVN_RUN) mvn spotless:apply

format-check: ## Vérifie le formatage (spotless:check)
	$(MVN_RUN) mvn spotless:check

clean: ## Nettoie les artefacts de build (mvn clean)
	$(MVN_RUN) mvn clean
