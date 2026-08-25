package app.network.message.connected;

import app.network.OutputJsonMessage;

public record AccountCreated(String login) implements OutputJsonMessage {

}
