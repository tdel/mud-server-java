package fr.idev.mudserver.controller;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.system.EncounterSystem;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.ActionNotFound;
import fr.idev.mudserver.network.message.ingame.CombatActionRequired;

@Component
public class ControllerDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ControllerDispatcher.class);

    private static final Set<String> COMBAT_ALLOWED_VERBS = Set.of("attack", "use", "look", "examine", "inventory",
            "stats");

    private final ControllerRegistry registry;
    private final EncounterSystem encounterSystem;

    public ControllerDispatcher(ControllerRegistry registry, EncounterSystem encounterSystem) {
        this.registry = registry;
        this.encounterSystem = encounterSystem;
    }

    public void dispatch(Connection connection, String actionName, String argument) {
        if (connection.state() == ConnectionState.INGAME) {
            CharacterInstance character = connection.character();
            if (character != null && encounterSystem.isInCombat(character)
                    && !COMBAT_ALLOWED_VERBS.contains(actionName)) {
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
