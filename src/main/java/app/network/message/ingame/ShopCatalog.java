package app.network.message.ingame;

import java.util.List;

import app.network.OutputJsonMessage;
import app.domain.item.Rarity;

public record ShopCatalog(String npcName, List<Entry> entries, int gold) implements OutputJsonMessage {

    public record Entry(String itemName, Rarity rarity, int price) {
    }

}
