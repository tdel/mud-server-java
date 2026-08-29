package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record PartyMemberJoined(UUID memberId, String memberName) implements OutputJsonMessage {

}
