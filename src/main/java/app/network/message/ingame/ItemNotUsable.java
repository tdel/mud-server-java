package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record ItemNotUsable(String name) implements OutputJsonMessage {

}
