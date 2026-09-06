package app.network.message.ingame;

import java.util.List;

import app.domain.world.CollisionGrid;
import app.domain.world.MapInstance;
import app.network.OutputJsonMessage;
import app.network.server.tcpjson.TcpJsonOutput;

/**
 * Carte statique complète d'une map (grille de collision), envoyée une fois au
 * client à l'entrée de la map (voir Portal, CharacterCreate, CharacterSelect) —
 * contrairement aux entités dynamiques de la map (et aux portails), poussées
 * séparément et scopées à AWARENESS_RANGE via {@link EntityAppeared}/
 * {@link EntityDisappeared} et {@link PortalAppeared}/{@link PortalDisappeared}
 * (voir {@link app.domain.actor.KnownList}).
 */
public record MapView(MapInstance map) implements OutputJsonMessage {

    public record CollisionGridView(int width, int height, double cellSize, List<String> walkableRows) {
    }

    public record Payload(String mapId, String mapName, CollisionGridView grid) {
    }

    @Override
    public void toJson(TcpJsonOutput output) {
        CollisionGrid grid = map.getCollisionGrid();
        CollisionGridView gridView = new CollisionGridView(grid.width(), grid.height(), grid.cellSize(),
                grid.toWalkableRows());

        output.write("MapView", new Payload(map.getId().toString(), map.getName(), gridView));
    }
}
