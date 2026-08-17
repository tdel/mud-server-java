package fr.idev.mudserver.domain.actor.instance;

import java.util.Objects;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.component.ShopComponent;
import fr.idev.mudserver.domain.actor.template.NpcTemplate;
import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.world.RoomInstance;

public final class NpcSellerInstance extends AbstractNpc {

    public NpcSellerInstance(UUID id, NpcTemplate template, RoomInstance room) {
        super(id, template, room);
        attachComponent(Objects.requireNonNull(template.shop()));
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
