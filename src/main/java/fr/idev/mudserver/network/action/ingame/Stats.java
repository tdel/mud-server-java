package fr.idev.mudserver.network.action.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.game.PlayerInstance;
import fr.idev.mudserver.network.ActionHandler;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.Session;
import fr.idev.mudserver.network.message.ingame.CharacterStats;

@Component
public class Stats implements ActionHandler {

    @Override
    public String name() {
        return "stats";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Session session, String argument) {
        PlayerInstance player = session.player();
        player.send(new CharacterStats(player.character()));
    }
}
