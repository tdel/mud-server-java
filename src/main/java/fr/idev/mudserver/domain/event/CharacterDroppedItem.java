package fr.idev.mudserver.domain.event;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.Room;

/**
 * Publié par {@link Character#dropItem(Item)}. Nommé
 * {@code CharacterDroppedItem} plutôt que {@code ItemDropped} pour ne pas
 * entrer en collision avec {@code network.message.ingame.ItemDropped}, le
 * message envoyé au joueur.
 */
public record CharacterDroppedItem(Character character, Item item, Room room) {
}
