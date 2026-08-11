package fr.idev.mudserver.controller.ingame;

import java.util.Set;

import org.springframework.stereotype.Component;

import java.util.Optional;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.GameCharacter;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.GamePlayerStats;
import fr.idev.mudserver.network.message.ingame.MonsterStatBlock;
import fr.idev.mudserver.network.message.ingame.NpcDescription;
import fr.idev.mudserver.network.message.ingame.TargetNotFound;

@Component
public class Examine implements ControllerHandler {

    private final RoomService roomService;

    public Examine(RoomService roomService) {
        this.roomService = roomService;
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
    public void onReceive(Connection connection, String argument) {
        GamePlayer character = connection.character();
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("examine <name>"));
            return;
        }

        Optional<GameCharacter> target = character.getCurrentRoom().findOccupantByName(name);

        if (target.isEmpty()) {
            connection.send(new TargetNotFound(name));
            return;
        }

        switch (target.get()) {
            case GamePlayer p -> connection.send(new GamePlayerStats(p));
            case GameMonster m -> connection.send(new MonsterStatBlock(m));
            case GameNpc n -> connection.send(new NpcDescription(n));
        }
    }
}
