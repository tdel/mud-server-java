package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record ItemNotEquipped(String name) implements OutputJsonMessage {

}
