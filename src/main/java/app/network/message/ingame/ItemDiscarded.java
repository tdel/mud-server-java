package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;
import app.domain.item.ItemGrade;

public record ItemDiscarded(UUID itemId, String name, ItemGrade grade) implements OutputJsonMessage {

}
