package app.domain.actor.event;

import app.domain.item.Item;
import app.domain.actor.instance.PlayerInstance;

// merged=true : item était un stack existant (item = la pile mise à jour, quantité déjà
// incrémentée) — le listener de persistance doit alors faire un updateQuantity, pas un insert.
public record ItemPurchased(PlayerInstance character, Item item, int price, boolean merged) {
}
