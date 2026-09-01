package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.game.engine.MovementEngine;
import app.network.CommandHandler;
import app.domain.world.MapPortal;
import app.domain.actor.instance.CharacterInstance;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.NoPortalHere;
import app.network.message.ingame.MapEnter;
import app.network.message.ingame.MapView;

@Component
public class Portal implements CommandHandler {

    private final MovementEngine movementEngine;

    public Portal(MovementEngine movementEngine) {
        this.movementEngine = movementEngine;
    }

    @Override
    public String name() {
        return "portal";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public boolean requiresAlive() {
        return true;
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        Optional<MapPortal> portal = character.getMotionSystem().getCurrentMap()
                .findPortalAt(character.getMotionSystem().getPosition());
        if (portal.isEmpty()) {
            connection.send(new NoPortalHere());
            return;
        }

        // Sans ça, un déplacement en cours (goto) au moment de franchir le portail
        // continue
        // à la prochaine tick de MovementEngine avec des waypoints calculés pour
        // l'ancienne
        // map/grille de collision, alors que la position du personnage vient de
        // changer de
        // map — le personnage se remet alors à marcher tout seul dans la map
        // d'arrivée
        // vers une destination périmée (demandé explicitement le 2026-08-28 : "le
        // passage
        // dans le téléporteur annule tout déplacement"). Le client doit recliquer pour
        // se
        // redéplacer (voir aussi Game._rebuild_map côté client, même correctif local).
        movementEngine.stopMovement(character);

        character.moveToMap(portal.get().targetMap(), portal.get().targetPosition());
        connection.send(new MapView(character.getMotionSystem().getCurrentMap()));
        connection.send(new MapEnter(character));
    }
}
