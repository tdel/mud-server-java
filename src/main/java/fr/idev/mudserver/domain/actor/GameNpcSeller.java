package fr.idev.mudserver.domain.actor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.RoomInstance;

public final class GameNpcSeller extends GameNpc {

    public GameNpcSeller(UUID id, NpcTemplate template, RoomInstance room) {
        super(id, template, room);
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
            // pas un index numérique : recherche par nom ci-dessous
        }
        return shop().items().stream().filter(entry -> entry.itemTemplate().getName().equalsIgnoreCase(trimmed))
                .findFirst();
    }

    public PurchaseOutcome sell(GamePlayer buyer, String input) {
        Optional<NpcShopEntry> entry = resolveEntry(input);
        if (entry.isEmpty()) {
            return new PurchaseOutcome.EntryNotFound();
        }

        Item item = new Item(UUID.randomUUID(), entry.get().itemTemplate(), buyer, null);
        boolean bought = buyer.buyItem(item, entry.get().price());
        return bought
                ? new PurchaseOutcome.Purchased(item, entry.get().price())
                : new PurchaseOutcome.InsufficientGold(entry.get().price());
    }

    public record NpcShop(List<NpcShopEntry> items) {
    }

    public record NpcShopEntry(ItemTemplate itemTemplate, int price) {
    }

    public sealed interface PurchaseOutcome {

        record Purchased(Item item, int price) implements PurchaseOutcome {
        }

        record EntryNotFound() implements PurchaseOutcome {
        }

        record InsufficientGold(int price) implements PurchaseOutcome {
        }
    }
}
