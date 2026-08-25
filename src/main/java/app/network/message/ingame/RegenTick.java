package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record RegenTick(int hpRestored, int manaRestored, int currentHealth, int maxHealth, int currentMana,
        int maxMana) implements OutputJsonMessage {

}
