package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

/**
 * Diffusé à toute la map (lanceur inclus) quand un sort projectile part :
 * chaque client anime localement la trajectoire jusqu'à la cible pendant
 * {@code travelDurationMs}, le serveur ne pousse aucune position intermédiaire.
 * {@code hit} : issue du jet (tiré dès le lancer), pour que les clients jouent
 * le bon effet (impact ou raté) à l'arrivée du projectile.
 */
public record SkillProjectileLaunched(UUID projectileId, UUID casterId, String casterName, UUID skillId,
        String skillName, double originX, double originY, UUID targetId, String targetName, double targetX,
        double targetY, long travelDurationMs, boolean hit) implements OutputJsonMessage {

}
