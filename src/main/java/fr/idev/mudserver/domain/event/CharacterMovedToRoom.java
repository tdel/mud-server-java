package fr.idev.mudserver.domain.event;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Room;

/**
 * Publié par {@link Character#moveToRoom(Room)} — un personnage déjà dans le
 * monde change de room. Voir {@link CharacterSpawnedToRoom} pour l'entrée
 * initiale, où il n'y a pas de room d'origine.
 */
public record CharacterMovedToRoom(Character character, Room from, Room to) {
}
