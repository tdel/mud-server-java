package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record KarmaChanged(int karma) implements OutputJsonMessage {
}
