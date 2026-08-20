package fr.idev.mudserver.network.command.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import java.util.Optional;

import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.GamePlayerStats;
import fr.idev.mudserver.network.message.ingame.MonsterStatBlock;
import fr.idev.mudserver.network.message.ingame.NpcDescription;
import fr.idev.mudserver.network.message.ingame.TargetNotFound;

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

        Optional<AbstractCharacter> target = character.getCurrentRoom().findOccupantByName(name);

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
