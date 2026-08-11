package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.WorldInstance;

/**
 * Publié par {@code WorldInstanceService.createInstance} une fois la
 * {@link WorldInstance} déjà matérialisée en mémoire (son graphe de
 * {@code RoomInstance} peuplé) — jamais avant, même principe que le reste du
 * domaine : mutation en mémoire d'abord, événement de persistance ensuite.
 */
public record WorldInstanceCreated(WorldInstance instance) {
}
