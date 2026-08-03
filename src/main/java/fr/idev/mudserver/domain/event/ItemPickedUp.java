package fr.idev.mudserver.domain.event;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Item;

/**
 * Publié par {@link Character#pickUpItem(Item)} une fois que celle-ci a déjà
 * tranché, sous verrou {@code synchronized(item)}, que {@code character}
 * remporte l'item — jamais avant.
 */
public record ItemPickedUp(Character character, Item item) {
}
