package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.ItemDropped;
import fr.idev.mudserver.network.message.ingame.ItemNotCarried;

@Component
public class Drop implements ControllerHandler {

    private final GameWorld gameWorld;

    public Drop(GameWorld gameWorld) {
        this.gameWorld = gameWorld;
    }

    @Override
    public String name() {
        return "drop";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        Character character = gameWorld.character(connection);
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("drop <name>"));
            return;
        }

        Optional<Item> item = character.findOneByName(name);

        if (item.isEmpty()) {
            connection.send(new ItemNotCarried(name));
            return;
        }

        String templateName = item.get().getName();
        character.dropItem(item.get());

        connection.send(new ItemDropped(templateName));
    }
}
