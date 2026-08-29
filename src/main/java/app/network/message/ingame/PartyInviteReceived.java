package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record PartyInviteReceived(UUID inviterId, String inviterName) implements OutputJsonMessage {

}
