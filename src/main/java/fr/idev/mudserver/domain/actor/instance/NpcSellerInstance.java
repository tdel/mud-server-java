package fr.idev.mudserver.domain.actor.instance;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.template.NpcTemplate;
import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.item.ItemTemplate;
import fr.idev.mudserver.domain.world.RoomInstance;

public final class NpcSellerInstance extends AbstractNpc {

    public NpcSellerInstance(UUID id, NpcTemplate template, RoomInstance room) {
        super(id, template, room);
        Objects.requireNonNull(template.shop());
    }

    public NpcShop shop() {
        return getTemplate().shop();
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
