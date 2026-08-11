package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.game.CombatEngine;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.ItemNotCarried;

/**
 * {@code use <item name>} — délègue entièrement à {@link CombatEngine#useItem},
 * qui gère aussi bien l'utilisation hors combat (immédiate) que pendant un
 * affrontement (coûte le tour, comme une attaque).
 */
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
        GamePlayer character = connection.character();
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
