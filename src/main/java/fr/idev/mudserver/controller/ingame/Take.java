package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.GamePlayer;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.RoomService;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.ItemNotFound;
import fr.idev.mudserver.network.message.ingame.ItemTaken;

@Component
public class Take implements ControllerHandler {

    private final GameWorld gameWorld;
    private final RoomService roomService;

    public Take(GameWorld gameWorld, RoomService roomService) {
        this.gameWorld = gameWorld;
        this.roomService = roomService;
    }

    @Override
    public String name() {
        return "take";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        GamePlayer character = gameWorld.character(connection);
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("take <name>"));
            return;
        }

        Optional<Item> item = character.getCurrentRoom().findOneByName(name);

        if (item.isEmpty()) {
            connection.send(new ItemNotFound(name));
            return;
        }

        if (!character.pickUpItem(item.get())) {
            // Quelqu'un d'autre l'a pris entre-temps — du point de vue de ce joueur,
            // indiscernable du fait qu'il n'était jamais là.
            connection.send(new ItemNotFound(name));
            return;
        }

        connection.send(new ItemTaken(item.get().getName()));
    }
}
