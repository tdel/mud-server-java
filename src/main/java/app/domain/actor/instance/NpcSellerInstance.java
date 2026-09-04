package app.domain.actor.instance;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import app.domain.actor.AbstractNpc;
import app.domain.actor.template.NpcTemplate;
import app.domain.item.Item;
import app.domain.item.ItemTemplate;
import app.domain.world.MapInstance;

public final class NpcSellerInstance extends AbstractNpc {

    public NpcSellerInstance(UUID id, NpcTemplate template, MapInstance map) {
        super(id, template, map);
        Objects.requireNonNull(template.shop());
    }

    public NpcShop shop() {
        return getTemplate().shop();
    }

    public Optional<NpcShopEntry> resolveEntry(String input) {
        String trimmed = input.trim();
        try {
            int index = Integer.parseInt(trimmed);
            if (index >= 1 && index <= shop().items().size()) {
                return Optional.of(shop().items().get(index - 1));
            }
        } catch (NumberFormatException ignored) {
            // pas un index numérique : recherche par uuid ou nom ci-dessous
        }
        try {
            UUID itemTemplateId = UUID.fromString(trimmed);
            return shop().items().stream().filter(entry -> entry.itemTemplate().getId().equals(itemTemplateId))
                    .findFirst();
        } catch (IllegalArgumentException ignored) {
            // pas un uuid : recherche par nom ci-dessous
        }
        return shop().items().stream().filter(entry -> entry.itemTemplate().getName().equalsIgnoreCase(trimmed))
                .findFirst();
    }

    public PurchaseOutcome sell(CharacterInstance buyer, String input) {
        return sell(buyer, input, 1);
    }

    // Une pile stackable (soulshot/spiritshot) est achetée en un seul Item de
    // quantité `quantity` (un seul débit/événement d'achat) ; un objet non
    // stackable (arme, potion, ...) est acheté un exemplaire à la fois en boucle
    // (un Item par exemplaire, cf. ItemType.stackable) — la solvabilité totale est
    // vérifiée une seule fois en amont pour que l'achat reste tout-ou-rien.
    public PurchaseOutcome sell(CharacterInstance buyer, String input, int quantity) {
        Optional<NpcShopEntry> entry = resolveEntry(input);
        if (entry.isEmpty()) {
            return new PurchaseOutcome.EntryNotFound();
        }

        int qty = Math.max(1, quantity);
        ItemTemplate template = entry.get().itemTemplate();
        int unitPrice = entry.get().price();
        int totalPrice = unitPrice * qty;
        if (buyer.getInventorySystem().getGold() < totalPrice) {
            return new PurchaseOutcome.InsufficientGold(totalPrice);
        }

        if (template.getType().stackable()) {
            Item item = new Item(UUID.randomUUID(), template, buyer, null, 0, qty);
            buyer.getInventorySystem().buyItem(item, totalPrice);
        } else {
            for (int i = 0; i < qty; i++) {
                buyer.getInventorySystem().buyItem(new Item(UUID.randomUUID(), template, buyer, null), unitPrice);
            }
        }
        return new PurchaseOutcome.Purchased();
    }

    public record NpcShop(List<NpcShopEntry> items) {
    }

    public record NpcShopEntry(ItemTemplate itemTemplate, int price) {
    }

    public sealed interface PurchaseOutcome {

        record Purchased() implements PurchaseOutcome {
        }

        record EntryNotFound() implements PurchaseOutcome {
        }

        record InsufficientGold(int price) implements PurchaseOutcome {
        }
    }
}
