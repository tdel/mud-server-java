package app.network.message.ingame;

import app.network.OutputJsonMessage;
import app.domain.item.Rarity;

public record ItemBought(String itemName, Rarity rarity, int price) implements OutputJsonMessage {

}
