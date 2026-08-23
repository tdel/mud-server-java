package fr.idev.mudserver.network.message.connected;

import fr.idev.mudserver.network.OutputJsonMessage;

public record AccountCreated(String login) implements OutputJsonMessage {

}
