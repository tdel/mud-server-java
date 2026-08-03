package fr.idev.mudserver.domain.event;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Room;

/**
 * Publié par {@link Character#spawnToRoom(Room)} — un personnage apparaît dans
 * une room sans room d'origine (création de personnage, entrée en jeu). Voir
 * {@link CharacterMovedToRoom} pour un déplacement entre deux rooms.
 */
public record CharacterSpawnedToRoom(Character character, Room room) {
}
