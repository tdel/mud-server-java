package fr.idev.mudserver.domain.event;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Item;

/**
 * Publié par {@link Character#unequipItem(Item)}. Nommé
 * {@code CharacterUnequippedItem} plutôt que {@code ItemUnequipped} pour ne pas
 * entrer en collision avec {@code network.message.ingame.ItemUnequipped}, le
 * message envoyé au joueur.
 */
public record CharacterUnequippedItem(Character character, Item item) {
}
