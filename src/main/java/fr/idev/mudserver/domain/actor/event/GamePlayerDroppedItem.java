package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.Room;

/**
 * Publié par {@link GamePlayer#dropItem(Item)}. Nommé
 * {@code GamePlayerDroppedItem} plutôt que {@code ItemDropped} pour ne pas
 * entrer en collision avec {@code network.message.ingame.ItemDropped}, le
 * message envoyé au joueur.
 */
public record GamePlayerDroppedItem(GamePlayer character, Item item, Room room) {
}
