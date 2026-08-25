package app.network.message.connected;

import app.network.OutputJsonMessage;

public record AccountAlreadyConnected(String login) implements OutputJsonMessage {

}
