package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record PlayerRespawned(String mapName, double x, double y, int currentHealth, int maxHealth, int currentMana,
        int maxMana) implements OutputJsonMessage {

}
