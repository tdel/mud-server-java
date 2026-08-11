package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.RoomInstance;

/**
 * Publié par {@link GamePlayer#spawnToRoom(RoomInstance)} — un personnage
 * apparaît dans une room sans room d'origine (création de personnage, entrée en
 * jeu). Voir {@link GamePlayerMovedToRoom} pour un déplacement entre deux
 * rooms.
 */
public record GamePlayerSpawnedToRoom(GamePlayer character, RoomInstance room) {
}
