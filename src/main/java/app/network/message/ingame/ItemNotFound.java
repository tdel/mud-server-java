package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record ItemNotFound(String name) implements OutputJsonMessage {

}
