package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record PartyMemberKicked(UUID targetId, String targetName) implements OutputJsonMessage {

}
