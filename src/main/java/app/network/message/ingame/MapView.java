package app.network.message.ingame;

import java.util.List;

import app.domain.world.CollisionGrid;
import app.domain.world.MapInstance;
import app.network.OutputJsonMessage;
import app.network.server.tcpjson.TcpJsonOutput;

/**
 * Carte statique complète d'une map (grille de collision + portails), envoyée
 * une fois au client à l'entrée de la map (voir Portal, CharacterCreate,
 * CharacterSelect) — contrairement à {@link MapEnter}, qui liste toutes les
 * entités dynamiques de la map au même instant (voir GamePlayerJoinedMap/
 * GamePlayerLeftMap/MonsterSpawned/MonsterDefeated pour leurs arrivées/départs
 * ultérieurs).
 */
public record MapView(MapInstance map) implements OutputJsonMessage {

    public record CollisionGridView(int width, int height, double cellSize, List<String> walkableRows) {
    }

    public record PortalView(double x, double y, double triggerRadius, String direction, String targetMapName) {
    }

    public record Payload(String mapId, String mapName, CollisionGridView grid, List<PortalView> portals) {
    }

    @Override
    public void toJson(TcpJsonOutput output) {
        CollisionGrid grid = map.getCollisionGrid();
        CollisionGridView gridView = new CollisionGridView(grid.width(), grid.height(), grid.cellSize(),
                grid.toWalkableRows());
        List<PortalView> portals = map.getPortals().stream().map(portal -> new PortalView(portal.position().x(),
                portal.position().y(), portal.triggerRadius(), portal.direction(), portal.targetMap().getName()))
                .toList();

        output.write("MapView", new Payload(map.getId().toString(), map.getName(), gridView, portals), false);
    }
}
