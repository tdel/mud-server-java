package app.domain;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import app.domain.actor.AbstractObject;
import app.domain.map.Position;
import app.domain.world.MapInstance;

/**
 * Un portail est un {@link AbstractObject} avec lequel un personnage peut
 * interagir (voir la commande {@code Portal}) : il a donc une identité stable
 * (id déterministe dérivé de la map source + position) qui lui permet de
 * participer au même mécanisme d'apparition/disparition que les personnages
 * (voir {@link app.domain.actor.KnownList}), plutôt que d'être poussé
 * inconditionnellement à l'entrée sur la map.
 */
public class MapPortal extends AbstractObject {

    // Tous les portails partagent le même nom ; le titre distingue la
    // destination (voir KnownList, qui affiche nom + titre).
    private static final String PORTAL_NAME = "Teleport";

    private final Position position;
    private final String direction;
    private final MapInstance sourceMap;
    private final MapInstance targetMap;
    private final Position targetPosition;
    private final double triggerRadius;

    public MapPortal(Position position, String direction, MapInstance sourceMap, MapInstance targetMap,
            Position targetPosition, double triggerRadius) {
        super(deterministicId(sourceMap.getId(), position), PORTAL_NAME);
        setTitle(targetMap.getName());
        this.position = position;
        this.direction = direction;
        this.sourceMap = sourceMap;
        this.targetMap = targetMap;
        this.targetPosition = targetPosition;
        this.triggerRadius = triggerRadius;
    }

    private static UUID deterministicId(UUID sourceMapId, Position position) {
        return UUID.nameUUIDFromBytes(
                (sourceMapId + ":portal:" + position.x() + "," + position.y()).getBytes(StandardCharsets.UTF_8));
    }

    public Position position() {
        return position;
    }

    public String direction() {
        return direction;
    }

    public MapInstance sourceMap() {
        return sourceMap;
    }

    public MapInstance targetMap() {
        return targetMap;
    }

    public Position targetPosition() {
        return targetPosition;
    }

    public double triggerRadius() {
        return triggerRadius;
    }
}
