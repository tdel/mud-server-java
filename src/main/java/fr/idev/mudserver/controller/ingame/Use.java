package fr.idev.mudserver.controller.ingame;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.game.CombatEngine;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.ItemNotCarried;
import fr.idev.mudserver.network.message.ingame.ItemUseRequiresCombat;

/**
 * {@code use <item name>} — stub pour cette itération : consomme le tour du
 * joueur (la cascade de {@link CombatEngine} a quand même lieu) sans aucun
 * effet réel, aucun système de consommable/soin n'existe encore sur
 * {@code ItemTemplate}. Gatée au combat, faute d'utilité en dehors (rien à
 * utiliser hors combat pour l'instant).
 */
@Component
public class Use implements ControllerHandler {

    private final GameWorld gameWorld;
    private final CombatEngine combatEngine;

    public Use(GameWorld gameWorld, CombatEngine combatEngine) {
        this.gameWorld = gameWorld;
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
        GamePlayer character = gameWorld.character(connection);
        String name = argument.trim();

        if (name.isEmpty()) {
            connection.send(new Usage("use <item name>"));
            return;
        }
        if (!character.isInCombat()) {
            connection.send(new ItemUseRequiresCombat());
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
