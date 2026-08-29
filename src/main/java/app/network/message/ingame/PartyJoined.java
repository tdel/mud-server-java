package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record PartyJoined(UUID leaderId, String leaderName, int memberCount) implements OutputJsonMessage {

}
