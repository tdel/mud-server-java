package fr.idev.mudserver.network.action.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.PlayerInstance;
import fr.idev.mudserver.network.ActionHandler;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.Session;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.CharacterNotFound;
import fr.idev.mudserver.network.message.ingame.CharacterStats;

@Component
public class Examine implements ActionHandler {

    private final GameWorld gameWorld;

    public Examine(GameWorld gameWorld) {
        this.gameWorld = gameWorld;
    }

    @Override
    public String name() {
        return "examine";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Session session, String argument) {
        PlayerInstance player = session.player();
        String name = argument.trim();

        if (name.isEmpty()) {
            player.send(new Usage("examine <name>"));
            return;
        }

        Character target = gameWorld.roomInstance(player.currentRoomId()).findCharacterByName(name);

        if (target == null) {
            player.send(new CharacterNotFound(name));
            return;
        }

        player.send(new CharacterStats(target));
    }
}
