package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record AlreadyInParty(String targetName) implements OutputJsonMessage {

}
