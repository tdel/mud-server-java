package app.network.message.ingame;

import app.network.OutputJsonMessage;

public record AttackOnCooldown(long remainingMillis) implements OutputJsonMessage {

}
