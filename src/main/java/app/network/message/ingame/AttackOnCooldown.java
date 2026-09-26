package app.network.message.ingame;

import app.network.OutputJsonMessage;

/**
 * Recharge de l'attaque de base. {@code rejected = false} : l'attaque vient
 * d'être portée et sa recharge démarre ; {@code rejected = true} : attaque
 * refusée car encore en recharge.
 */
public record AttackOnCooldown(long remainingMillis, boolean rejected) implements OutputJsonMessage {

}
