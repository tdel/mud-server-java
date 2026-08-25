package app.network.message.connected;

import app.network.OutputJsonMessage;

public record AccountNotFound(String login) implements OutputJsonMessage {

}
