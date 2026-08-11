package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.RoomInstance;

/**
 * Publié par {@link GamePlayer#moveToRoom(RoomInstance)} — un personnage déjà
 * dans le monde change de room. Voir {@link GamePlayerSpawnedToRoom} pour
 * l'entrée initiale, où il n'y a pas de room d'origine.
 */
public record GamePlayerMovedToRoom(GamePlayer character, RoomInstance from, RoomInstance to) {
}
