package app.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import app.network.CommandHandler;
import app.domain.actor.instance.MonsterInstance;
import app.domain.actor.instance.CharacterInstance;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.TargetNotFound;
import app.network.message.ingame.TargetSelected;

@Component
public class Select implements CommandHandler {

    @Override
    public String name() {
        return "select";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("select <monster name>"));
            return;
        }

        Optional<MonsterInstance> target = character.getCurrentZone().findMonsterByName(name);
        if (target.isEmpty()) {
            connection.send(new TargetNotFound(name));
            return;
        }

        character.getCombat().setTarget(target.get());
        connection.send(new TargetSelected(target.get().getName()));
    }
}
