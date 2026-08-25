package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record ItemNotEquippable(String name) implements OutputJsonMessage {

}
