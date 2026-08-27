package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

/**
 * Diffusé à toute la zone (lanceur inclus) quand un sort projectile part :
 * chaque client anime localement la trajectoire jusqu'à la cible pendant
 * {@code travelDurationMs}, le serveur ne pousse aucune position intermédiaire.
 */
public record SpellProjectileLaunched(UUID projectileId, UUID casterId, String casterName, UUID spellId,
        String spellName, double originX, double originY, UUID targetId, String targetName, double targetX,
        double targetY, long travelDurationMs) implements OutputJsonMessage {

}
