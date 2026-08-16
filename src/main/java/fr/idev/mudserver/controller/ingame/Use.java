package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.game.CombatEngine;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.ItemNotCarried;

@Component
public class Use implements ControllerHandler {

    private final CombatEngine combatEngine;

    public Use(CombatEngine combatEngine) {
        this.combatEngine = combatEngine;
    }

    @Override
    public String name() {
        return "use";
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
            connection.send(new Usage("use <item name>"));
            return;
        }

        Optional<Item> item = character.getInventory().findOneByName(name);
        if (item.isEmpty()) {
            connection.send(new ItemNotCarried(name));
            return;
        }

        combatEngine.useItem(character, item.get());
    }
}
