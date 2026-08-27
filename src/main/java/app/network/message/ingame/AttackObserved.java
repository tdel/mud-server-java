package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record AttackObserved(UUID attackerId, String attackerName, UUID targetId, String targetName, boolean hit,
        boolean critical, int damage, boolean targetDefeated) implements OutputJsonMessage {

}
