package fr.idev.mudserver.domain.actor.component;

import java.util.List;

import fr.idev.mudserver.domain.item.ItemTemplate;

public class ShopComponent {

    public List<ShopEntry> items;

    public ShopComponent(List<ShopEntry> items) {
        this.items = items;
    }

    public record ShopEntry(ItemTemplate itemTemplate, int price) {
    }
}
