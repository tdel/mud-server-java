package fr.idev.mudserver.domain.actor.component;

import java.util.List;

import fr.idev.mudserver.domain.item.ItemTemplate;

public record ShopComponent(List<ShopEntry> items) {

    public record ShopEntry(ItemTemplate itemTemplate, int price) {
    }
}
