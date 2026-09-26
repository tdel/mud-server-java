package app.network.message.ingame;

import app.network.OutputJsonMessage;

/**
 * Recharge d'un sort. {@code rejected = false} : le sort vient d'être lancé et
 * sa recharge démarre ; {@code rejected = true} : le joueur a tenté de le
 * relancer alors qu'il est encore en recharge, la commande est refusée.
 */
public record SkillOnCooldown(String skillName, long remainingMillis, boolean rejected) implements OutputJsonMessage {

}
