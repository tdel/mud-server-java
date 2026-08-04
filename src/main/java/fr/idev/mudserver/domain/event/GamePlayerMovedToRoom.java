package fr.idev.mudserver.domain.event;

import fr.idev.mudserver.domain.GamePlayer;
import fr.idev.mudserver.domain.Room;

/**
 * Publié par {@link GamePlayer#moveToRoom(Room)} — un personnage déjà dans le
 * monde change de room. Voir {@link GamePlayerSpawnedToRoom} pour l'entrée
 * initiale, où il n'y a pas de room d'origine.
 */
public record GamePlayerMovedToRoom(GamePlayer character, Room from, Room to) {
}
