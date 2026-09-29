package app.network.message.ingame;

import java.util.List;

import app.domain.map.Position;
import app.network.OutputJsonMessage;

/**
 * Début (ou changement) de déplacement du personnage du client. {@code x/y} :
 * destination finale ; {@code startX/startY} : position serveur au départ,
 * rattrapée jusqu'à l'instant de la commande (voir MovementEngine.settle) ;
 * {@code waypoints} : chemin lissé complet suivi par le serveur (destination
 * incluse), pour que le client suive exactement la même trajectoire au lieu de
 * filer en ligne droite vers la destination et d'être recalé autour des
 * obstacles.
 */
public record MovementStarted(double x, double y, double heading, double startX, double startY,
        List<Position> waypoints) implements OutputJsonMessage {

}
