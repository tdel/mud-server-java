package fr.idev.mudserver.network.message.connected;

import fr.idev.mudserver.network.OutputJsonMessage;

public record AccountNotFound(String login) implements OutputJsonMessage {

}
