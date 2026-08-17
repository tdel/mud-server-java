package fr.idev.mudserver.domain.actor.instance;

import java.util.UUID;

import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.item.Item;

public final class NpcSellerInstance extends AbstractNpc {

    // Composant requis en plus (voir AbstractNpc) : ShopComponent (non-nul)
    public NpcSellerInstance(UUID id) {
        super(id);
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
