package app.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import app.network.message.ActionNotFound;
import app.network.message.ingame.AlreadyCasting;
import app.network.message.ingame.CharacterIsDead;

@Component
public class CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);

    private final CommandRegistry registry;

    public CommandDispatcher(CommandRegistry registry) {
        this.registry = registry;
    }

    public void dispatch(Connection connection, String actionName, String argument) {
        registry.find(connection.state(), actionName).ifPresentOrElse(action -> {
            if (connection.state() == ConnectionState.INGAME && action.requiresAlive()
                    && connection.character().getCurrentHealth() <= 0) {
                log.debug("command.rejected verb={} reason=character_dead", actionName);
                connection.send(new CharacterIsDead());
                return;
            }
            if (connection.state() == ConnectionState.INGAME && action.requiresNotCasting()
                    && connection.character().getSkillSystem().isCasting()) {
                log.debug("command.rejected verb={} reason=character_casting", actionName);
                connection.send(new AlreadyCasting());
                return;
            }
            log.info("command.received verb={} state={}", actionName, connection.state());
            action.onReceive(connection, argument);
        }, () -> {
            log.debug("command.unknown verb={} state={}", actionName, connection.state());
            connection.send(new ActionNotFound());
        });
    }
}
