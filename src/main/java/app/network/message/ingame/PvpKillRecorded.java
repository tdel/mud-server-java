package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record PvpKillRecorded(int pvpCount) implements OutputJsonMessage {
}
