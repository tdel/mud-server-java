package fr.idev.mudserver.network.message.ingame;

import java.util.List;

import fr.idev.mudserver.domain.world.CollisionGrid;
import fr.idev.mudserver.domain.world.ZoneInstance;
import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.tcpjson.TcpJsonOutput;

/**
 * Carte statique complète d'une zone (grille de collision + portails), envoyée
 * une fois au client à l'entrée de la zone (voir Portal, CharacterCreate,
 * CharacterSelect) — contrairement à {@link ViewAround}, rejouée à chaque tick
 * avec uniquement le viewport dynamique (entités, chemin) autour du joueur.
 */
public record ZoneMap(ZoneInstance zone) implements OutputJsonMessage {

    public record CollisionGridView(int width, int height, double cellSize, List<String> walkableRows) {
    }

    public record PortalView(double x, double y, double triggerRadius, String direction, String targetZoneName) {
    }

    public record Payload(String zoneId, String zoneName, CollisionGridView grid, List<PortalView> portals) {
    }

    @Override
    public void toJson(TcpJsonOutput output) {
        CollisionGrid grid = zone.getCollisionGrid();
        CollisionGridView gridView = new CollisionGridView(grid.width(), grid.height(), grid.cellSize(),
                grid.toWalkableRows());
        List<PortalView> portals = zone.getPortals().stream().map(portal -> new PortalView(portal.position().x(),
                portal.position().y(), portal.triggerRadius(), portal.direction(), portal.targetZone().getName()))
                .toList();

        output.write("ZoneMap", new Payload(zone.getId().toString(), zone.getName(), gridView, portals), false);
    }
}
