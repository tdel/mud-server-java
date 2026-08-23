package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.item.Rarity;

public record ItemBought(String itemName, Rarity rarity, int price) implements OutputJsonMessage {

}
