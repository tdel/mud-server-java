package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record NewPartyLeader(UUID leaderId, String leaderName) implements OutputJsonMessage {

}
