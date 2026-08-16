package fr.idev.mudserver.domain.actor.system;

import java.util.Optional;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.NpcSellerInstance;
import fr.idev.mudserver.domain.actor.instance.NpcSellerInstance.NpcShopEntry;
import fr.idev.mudserver.domain.actor.instance.NpcSellerInstance.PurchaseOutcome;
import fr.idev.mudserver.domain.item.Item;

public final class ShopSystem {

    private ShopSystem() {
    }

    public static Optional<NpcShopEntry> resolveEntry(NpcSellerInstance npc, String input) {
        String trimmed = input.trim();
        try {
            int index = Integer.parseInt(trimmed);
            if (index >= 1 && index <= npc.shop().items().size()) {
                return Optional.of(npc.shop().items().get(index - 1));
            }
        } catch (NumberFormatException ignored) {
            // pas un index numérique : recherche par nom ci-dessous
        }
        return npc.shop().items().stream().filter(entry -> entry.itemTemplate().getName().equalsIgnoreCase(trimmed))
                .findFirst();
    }

    public static PurchaseOutcome sell(NpcSellerInstance npc, CharacterInstance buyer, String input) {
        Optional<NpcShopEntry> entry = resolveEntry(npc, input);
        if (entry.isEmpty()) {
            return new PurchaseOutcome.EntryNotFound();
        }

        Item item = new Item(UUID.randomUUID(), entry.get().itemTemplate(), buyer, null);
        boolean bought = InventorySystem.buyItem(buyer, item, entry.get().price());
        return bought
                ? new PurchaseOutcome.Purchased(item, entry.get().price())
                : new PurchaseOutcome.InsufficientGold(entry.get().price());
    }

}
