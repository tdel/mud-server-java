package fr.idev.mudserver.controller.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.PlayerInstance;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ingame.CharacterStats;

@Component
public class Stats implements ControllerHandler {

    private final GameWorld gameWorld;

    public Stats(GameWorld gameWorld) {
        this.gameWorld = gameWorld;
    }

    @Override
    public String name() {
        return "stats";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection session, String argument) {
        PlayerInstance player = gameWorld.player(session);
        player.send(new CharacterStats(player.character()));
    }
}
