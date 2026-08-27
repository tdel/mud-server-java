package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.item.Rarity;

public record ItemDiscarded(UUID itemId, String name, Rarity rarity) implements OutputJsonMessage {

}
