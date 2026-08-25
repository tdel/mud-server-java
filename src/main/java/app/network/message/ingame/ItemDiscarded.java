package app.network.message.ingame;

import app.network.OutputJsonMessage;
import app.domain.item.Rarity;

public record ItemDiscarded(String name, Rarity rarity) implements OutputJsonMessage {

}
