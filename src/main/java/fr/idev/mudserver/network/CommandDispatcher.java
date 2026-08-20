package fr.idev.mudserver.network;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.network.message.ActionNotFound;
import fr.idev.mudserver.network.message.ingame.CombatActionRequired;

@Component
public class CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);

    private static final Set<String> COMBAT_ALLOWED_VERBS = Set.of("attack", "use", "look", "examine", "inventory",
            "stats");

    private final CommandRegistry registry;

    public CommandDispatcher(CommandRegistry registry) {
        this.registry = registry;
    }

    public void dispatch(Connection connection, String actionName, String argument) {
        if (connection.state() == ConnectionState.INGAME) {
            CharacterInstance character = connection.character();
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
