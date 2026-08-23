package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;

public record RegenTick(int hpRestored, int manaRestored, int currentHealth, int maxHealth, int currentMana,
        int maxMana) implements OutputJsonMessage {

}
