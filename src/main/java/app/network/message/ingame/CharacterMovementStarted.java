package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.domain.map.Position;
import app.network.OutputJsonMessage;

/**
 * Diffusé au reste de la map quand un AUTRE personnage démarre un déplacement
 * (`goto`, poursuite/retour d'un monstre), pour que les clients puissent
 * interpoler localement sa trajectoire plutôt que d'attendre une position
 * poussée à chaque tick. {@code x/y} : position serveur actuelle (recalage doux
 * côté client) ; {@code waypoints} : chemin restant jusqu'à
 * {@code targetX/targetY} inclus.
 */
public record CharacterMovementStarted(UUID characterId, String characterName, double x, double y, double targetX,
        double targetY, double heading, List<Position> waypoints) implements OutputJsonMessage {

}
