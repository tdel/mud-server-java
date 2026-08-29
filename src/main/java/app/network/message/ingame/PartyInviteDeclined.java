package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record PartyInviteDeclined(String otherName) implements OutputJsonMessage {

}
