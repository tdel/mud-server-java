package app.network.message.ingame;

import java.util.UUID;

import app.domain.MapPortal;

/**
 * Analogue à {@link EntityView}, mais pour un {@link MapPortal} (statique, pas
 * de santé/vitesse).
 */
public record PortalView(UUID id, double x, double y, double triggerRadius, String direction, String targetMapName,
        String name, String title) {

    public static PortalView of(MapPortal portal) {
        return new PortalView(portal.getId(), portal.position().x(), portal.position().y(), portal.triggerRadius(),
                portal.direction(), portal.targetMap().getName(), portal.getName(), portal.getTitle());
    }
}
