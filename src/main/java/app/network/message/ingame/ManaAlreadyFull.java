package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record ManaAlreadyFull(String name, int currentMana, int maxMana) implements OutputJsonMessage {

}
