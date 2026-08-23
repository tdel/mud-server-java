package fr.idev.mudserver.network.message.ingame;

import java.util.List;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.item.Rarity;

public record ShopCatalog(String npcName, List<Entry> entries, int gold) implements OutputJsonMessage {

    public record Entry(String itemName, Rarity rarity, int price) {
    }

}
