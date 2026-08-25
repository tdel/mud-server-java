package app.network.message.ingame;

import app.network.OutputJsonMessage;
import app.domain.item.Rarity;

public record ItemUnequipped(String name, Rarity rarity) implements OutputJsonMessage {

}
