package app.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import java.util.Optional;

import app.network.CommandHandler;
import app.domain.actor.AbstractCharacter;
import app.domain.actor.instance.MonsterInstance;
import app.domain.actor.AbstractNpc;
import app.domain.actor.instance.CharacterInstance;
import app.network.Connection;
import app.network.ConnectionState;
import app.network.message.Usage;
import app.network.message.ingame.GamePlayerStats;
import app.network.message.ingame.MonsterStatBlock;
import app.network.message.ingame.NpcDescription;
import app.network.message.ingame.TargetNotFound;

@Component
public class Examine implements CommandHandler {

    @Override
    public String name() {
        return "examine";
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
            connection.send(new Usage("examine <name>"));
            return;
        }

        Optional<AbstractCharacter> target = character.getCurrentZone().findOccupantByName(name);

        if (target.isEmpty()) {
            connection.send(new TargetNotFound(name));
            return;
        }

        switch (target.get()) {
            case CharacterInstance p -> connection.send(new GamePlayerStats(p));
            case MonsterInstance m -> connection.send(new MonsterStatBlock(m));
            case AbstractNpc n -> connection.send(new NpcDescription(n));
            default -> throw new IllegalStateException("Type de cible inattendu : " + target.get().getClass());
        }
    }
}
