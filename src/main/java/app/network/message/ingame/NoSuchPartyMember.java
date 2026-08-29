package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record NoSuchPartyMember(String targetName) implements OutputJsonMessage {

}
