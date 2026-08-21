package fr.idev.mudserver.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.NoTargetSelected;
import fr.idev.mudserver.network.message.ingame.TargetNotFound;

@Component
public class Attack implements CommandHandler {

    @Override
    public String name() {
        return "attack";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        String name = argument.trim();

        MonsterInstance target;
        if (name.isEmpty()) {
            target = character.getTarget();
            if (target == null) {
                connection.send(new NoTargetSelected());
                return;
            }
            if (!character.getCurrentRoom().getMonsters().contains(target)) {
                character.setTarget(null);
                connection.send(new TargetNotFound(target.getName()));
                return;
            }
        } else {
            Optional<MonsterInstance> found = character.getCurrentRoom().findMonsterByName(name);
            if (found.isEmpty()) {
                connection.send(new TargetNotFound(name));
                return;
            }
            target = found.get();
            character.setTarget(target);
        }

        // do nothing now
    }
}
