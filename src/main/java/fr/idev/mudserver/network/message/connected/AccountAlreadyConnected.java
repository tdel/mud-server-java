package fr.idev.mudserver.network.message.connected;

import fr.idev.mudserver.network.OutputJsonMessage;

public record AccountAlreadyConnected(String login) implements OutputJsonMessage {

}
