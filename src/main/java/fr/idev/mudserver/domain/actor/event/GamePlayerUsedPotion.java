package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.actor.GamePlayer;

/**
 * Publié par {@code ConsumableItem#consume} une fois les PV déjà mis à jour et
 * l'objet retiré de l'inventaire en mémoire — {@code CharacterService} persiste
 * les PV et confirme au joueur, {@code ItemService} supprime la ligne DB de
 * l'objet consommé. Préfixe {@code GamePlayerXxx}, pas {@code
 * ItemXxx}, pour la même raison que
 * {@link GamePlayerEquippedItem}/{@link GamePlayerDroppedItem} : éviter la
 * collision avec les messages réseau {@code network.message.ingame.ItemXxx}.
 */
public record GamePlayerUsedPotion(GamePlayer character, Item item, int healedAmount) {
}
