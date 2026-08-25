package app.network.message.connected;

import app.network.OutputJsonMessage;

public record WelcomeBack(String login) implements OutputJsonMessage {

}
