package fr.idev.mudserver.domain.event;

import fr.idev.mudserver.domain.GamePlayer;
import fr.idev.mudserver.domain.Item;

/**
 * Publié par {@link GamePlayer#pickUpItem(Item)} une fois que celle-ci a déjà
 * tranché, sous verrou {@code synchronized(item)}, que {@code character}
 * remporte l'item — jamais avant.
 */
public record ItemPickedUp(GamePlayer character, Item item) {
}
