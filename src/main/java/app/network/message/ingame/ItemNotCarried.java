package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record ItemNotCarried(String name) implements OutputJsonMessage {

}
