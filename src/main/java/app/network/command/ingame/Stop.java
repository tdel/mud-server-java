package app.network.command.ingame;

import java.util.Set;

import app.game.engine.MovementEngine;
import app.game.engine.SkillCastEngine;
import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.domain.actor.instance.CharacterInstance;
import app.network.Connection;
import app.network.ConnectionState;

@Component
public class Stop implements CommandHandler {

    private final MovementEngine movementEngine;
    private final SkillCastEngine skillCastEngine;

    public Stop(MovementEngine movementEngine, SkillCastEngine skillCastEngine) {
        this.movementEngine = movementEngine;
        this.skillCastEngine = skillCastEngine;
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
        skillCastEngine.cancelCast(character);
    }
}
