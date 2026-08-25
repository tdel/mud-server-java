package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record AttackObserved(String attackerName, String targetName, boolean hit, boolean critical, int damage,
        boolean targetDefeated) implements OutputJsonMessage {

}
