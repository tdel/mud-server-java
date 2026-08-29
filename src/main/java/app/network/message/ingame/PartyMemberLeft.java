package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record PartyMemberLeft(String memberName) implements OutputJsonMessage {

}
