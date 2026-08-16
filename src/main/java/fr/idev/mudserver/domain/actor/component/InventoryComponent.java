package fr.idev.mudserver.domain.actor.component;

import java.util.List;

import fr.idev.mudserver.domain.item.Item;

public record InventoryComponent(List<Item> items, int gold) {
}
