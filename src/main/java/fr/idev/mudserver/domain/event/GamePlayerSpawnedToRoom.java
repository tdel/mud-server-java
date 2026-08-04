package fr.idev.mudserver.domain.event;

import fr.idev.mudserver.domain.GamePlayer;
import fr.idev.mudserver.domain.Room;

/**
 * Publié par {@link GamePlayer#spawnToRoom(Room)} — un personnage apparaît dans
 * une room sans room d'origine (création de personnage, entrée en jeu). Voir
 * {@link GamePlayerMovedToRoom} pour un déplacement entre deux rooms.
 */
public record GamePlayerSpawnedToRoom(GamePlayer character, Room room) {
}
