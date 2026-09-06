package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record PlayerKillRecorded(int pkCount) implements OutputJsonMessage {
}
