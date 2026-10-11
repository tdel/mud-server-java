package app.network.message.ingame;

import app.network.OutputJsonMessage;

// Ramené à la vie sur place (Resurrection), avec ces PV/PM.
public record PlayerResurrected(String casterName, double x, double y, int currentHealth, int maxHealth,
        int currentMana, int maxMana) implements OutputJsonMessage {
}
