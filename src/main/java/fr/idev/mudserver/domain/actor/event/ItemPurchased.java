package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.actor.GamePlayer;

/**
 * Publié par {@link GamePlayer#buyItem} une fois {@code item} déjà attaché à
 * {@code character} et ajouté à son inventaire — comme
 * {@link CharacterLootedItem}, {@code item} n'a encore aucune ligne en base
 * ({@code game.ItemService} attache son template puis l'insère), mais reste un
 * événement distinct pour que le message envoyé au joueur (« vous achetez »)
 * diffère de celui d'un butin (« vous looté »).
 */
public record ItemPurchased(GamePlayer character, Item item, int price) {
}
