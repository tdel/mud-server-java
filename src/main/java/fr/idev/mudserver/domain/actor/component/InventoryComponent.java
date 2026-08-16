package fr.idev.mudserver.domain.actor.component;

import java.util.List;
import java.util.Optional;

import fr.idev.mudserver.domain.item.Item;

public record InventoryComponent(List<Item> items, int gold) {

    public Optional<Item> findOneByName(String name) {
        return items.stream().filter(item -> item.getName().equalsIgnoreCase(name)).findFirst();
    }

    public List<Item> carriedItems() {
        return items.stream().filter(item -> item.getSlot() == null).toList();
    }

    public List<Item> equippedItems() {
        return items.stream().filter(item -> item.getSlot() != null).toList();
    }
}
