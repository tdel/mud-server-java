package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.actor.GamePlayer;

/**
 * Publié par {@link GamePlayer#receiveLootItem(Item)} une fois {@code item}
 * déjà attaché à {@code character} et ajouté à son inventaire — {@code item}
 * n'a pas encore de ligne en base (contrairement à {@code ItemPickedUp}, qui ne
 * fait que déplacer un item déjà persisté) : {@code game.ItemService} attache
 * son {@code ItemTemplate} puis l'insère.
 */
public record CharacterLootedItem(GamePlayer character, Item item) {
}
