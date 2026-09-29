package app.network.message.ingame;

import app.network.OutputJsonMessage;

/**
 * Délai de réutilisation d'un objet (Scroll of Escape...), même sémantique que
 * {@link SkillOnCooldown} : {@code rejected = false} à l'utilisation, la
 * recharge démarre ; {@code rejected = true} : nouvelle tentative refusée tant
 * qu'elle n'est pas écoulée.
 */
public record ItemOnCooldown(String name, long remainingMillis, boolean rejected) implements OutputJsonMessage {

}
