package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record XpGained(int amount, int xp, int xpForCurrentLevel, int xpForNextLevel) implements OutputJsonMessage {

}
