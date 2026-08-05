package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.Item;

/**
 * Publié par {@link GamePlayer#unequipItem(Item)}. Nommé
 * {@code GamePlayerUnequippedItem} plutôt que {@code ItemUnequipped} pour ne
 * pas entrer en collision avec {@code network.message.ingame.ItemUnequipped},
 * le message envoyé au joueur.
 */
public record GamePlayerUnequippedItem(GamePlayer character, Item item) {
}
