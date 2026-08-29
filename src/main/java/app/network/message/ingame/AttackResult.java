package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

public record AttackResult(UUID attackerId, String attackerName, UUID targetId, String targetName, boolean hit,
        boolean critical, int damage, int targetCurrentHealth) implements OutputJsonMessage {

}
