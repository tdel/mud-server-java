package app.network.command.ingame;

import java.util.Set;

import app.game.engine.MovementEngine;
import app.game.engine.SpellCastEngine;
import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.domain.actor.instance.CharacterInstance;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.ingame.CharacterMovementStopped;
import app.network.message.ingame.MovementStopped;

@Component
public class Stop implements CommandHandler {

    private final MovementEngine movementEngine;
    private final SpellCastEngine spellCastEngine;

    public Stop(MovementEngine movementEngine, SpellCastEngine spellCastEngine) {
        this.movementEngine = movementEngine;
        this.spellCastEngine = spellCastEngine;
    }

    @Override
    public String name() {
        return "stop";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();

        movementEngine.stopMovement(character);
        spellCastEngine.cancelCast(character);

        connection.send(new MovementStopped(character.getPosition().x(), character.getPosition().y()));
        character.getCurrentZone().broadcast(new CharacterMovementStopped(character.getId(), character.getName(),
                character.getPosition().x(), character.getPosition().y()), character);
    }
}
