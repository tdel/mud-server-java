package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record XpGained(int amount) implements OutputJsonMessage {

}
