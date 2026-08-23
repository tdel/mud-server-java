package fr.idev.mudserver.network.command.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.TargetNotFound;
import fr.idev.mudserver.network.message.ingame.TargetSelected;

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
