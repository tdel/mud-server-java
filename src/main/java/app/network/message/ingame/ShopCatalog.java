package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.item.Rarity;

public record ShopCatalog(UUID npcId, String npcName, List<Entry> entries, int gold) implements OutputJsonMessage {

    public record Entry(UUID itemTemplateId, String itemName, Rarity rarity, int price) {
    }

}
