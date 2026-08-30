SHELL := /bin/bash

# make exécute chaque ligne de recette dans un shell non interactif : ~/.bashrc
# (où SDKMAN ajoute mvn/java au PATH) n'est jamais sourcé. On pointe donc
# directement vers les liens "current" de SDKMAN.
export JAVA_HOME := $(HOME)/.sdkman/candidates/java/current
export PATH := $(JAVA_HOME)/bin:$(HOME)/.sdkman/candidates/maven/current/bin:$(PATH)

.PHONY: help build package test run stop format format-check clean install-linux

help: ## Affiche cette aide
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "}; {printf "%-14s %s\n", $$1, $$2}'

install-linux: ## Installe SDKMAN, Java 25 (Temurin) et Maven (idempotent)
	@if [ ! -d "$$HOME/.sdkman" ]; then \
		echo "Installation de SDKMAN..."; \
		curl -s "https://get.sdkman.io" | bash; \
	else \
		echo "SDKMAN déjà installé."; \
	fi
	@source "$$HOME/.sdkman/bin/sdkman-init.sh" && \
		if [ ! -d "$$HOME/.sdkman/candidates/java/25-tem" ]; then \
			echo "Installation de Java 25-tem..."; \
			sdk install java 25-tem; \
		else \
			echo "Java 25-tem déjà installé."; \
		fi && \
		if [ ! -d "$$HOME/.sdkman/candidates/maven/current" ]; then \
			echo "Installation de Maven..."; \
			sdk install maven; \
		else \
			echo "Maven déjà installé."; \
		fi && \
		sdk default java 25-tem
	@echo "Installation terminée. Ouvrez un nouveau terminal (ou : source ~/.sdkman/bin/sdkman-init.sh), puis : make package"

build: package ## Alias de `package`

package: ## Build le projet (mvn package)
	mvn package

test: ## Lance la suite de tests
	mvn test

run: ## Démarre le serveur telnet (mvn spring-boot:run)
	mvn spring-boot:run

stop: ## Arrête le serveur lancé par `make run` (process mvn + JVM forkée + ports résiduels)
	@for p in $$(pgrep -f 'spring-boot:run|app\.ServerApplication'); do \
		if [ "$$(ps -o comm= -p $$p 2>/dev/null)" = "java" ]; then \
			echo "Arrêt du process Java $$p..."; \
			kill -9 $$p 2>/dev/null; \
		fi; \
	done
	@fuser -k 4002/tcp 8080/tcp 8081/tcp 2>/dev/null; true
	@echo "Serveur arrêté."

format: ## Formate le code (spotless:apply)
	mvn spotless:apply

format-check: ## Vérifie le formatage (spotless:check)
	mvn spotless:check

clean: ## Nettoie les artefacts de build (mvn clean)
	mvn clean
