package fr.idev.mudserver.controller;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ActionNotFound;
import fr.idev.mudserver.network.message.ingame.CombatActionRequired;

/**
 * Point de passage unique de chaque commande, avant même la résolution par
 * {@link ControllerRegistry} — c'est ici qu'est appliqué le verrouillage des
 * commandes pendant un combat actif ({@link GamePlayer#isInCombat()}), plutôt
 * que dans chacun des {@code ControllerHandler} : un seul endroit à modifier,
 * aucun des handlers existants n'a besoin de connaître l'état de combat. Le
 * refus « ce n'est pas votre tour » reste en revanche porté par
 * {@code game.CombatEngine} (via {@code controller.ingame.Attack}/ {@code Use})
 * : ce dispatcher ne connaît que les verbes, pas l'état des tours.
 */
@Component
public class ControllerDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ControllerDispatcher.class);

    private static final Set<String> COMBAT_ALLOWED_VERBS = Set.of("attack", "use", "look", "examine", "inventory",
            "stats");

    private final ControllerRegistry registry;

    public ControllerDispatcher(ControllerRegistry registry) {
        this.registry = registry;
    }

    public void dispatch(Connection connection, String actionName, String argument) {
        if (connection.state() == ConnectionState.INGAME) {
            GamePlayer character = connection.character();
            if (character != null && character.isInCombat() && !COMBAT_ALLOWED_VERBS.contains(actionName)) {
                log.debug("combat.action_blocked verb={} character={}", actionName, character.getName());
                connection.send(new CombatActionRequired());
                return;
            }
        }

        registry.find(connection.state(), actionName).ifPresentOrElse(action -> action.onReceive(connection, argument),
                () -> {
                    log.debug("command.unknown verb={} state={}", actionName, connection.state());
                    connection.send(new ActionNotFound());
                });
    }
}
