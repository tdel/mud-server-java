package fr.idev.mudserver.domain.event;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Item;

/**
 * Publié par {@link Character#pickUpItem(Item)} une fois que
 * {@code game.ItemService#addItemToInventory} a déjà tranché, sous verrou
 * pessimiste, que {@code character} remporte l'item — jamais avant.
 */
public record ItemPickedUp(Character character, Item item) {
}
