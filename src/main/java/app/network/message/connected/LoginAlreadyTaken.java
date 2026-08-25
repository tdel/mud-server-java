package app.network.message.connected;

import app.network.OutputJsonMessage;

public record LoginAlreadyTaken(String login) implements OutputJsonMessage {

}
