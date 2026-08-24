package fr.idev.mudserver.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.message.ActionNotFound;

@Component
public class CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);

    private final CommandRegistry registry;

    public CommandDispatcher(CommandRegistry registry) {
        this.registry = registry;
    }

    public void dispatch(Connection connection, String actionName, String argument) {
        registry.find(connection.state(), actionName).ifPresentOrElse(action -> {
            log.info("command.received verb={} state={}", actionName, connection.state());
            action.onReceive(connection, argument);
        }, () -> {
            log.debug("command.unknown verb={} state={}", actionName, connection.state());
            connection.send(new ActionNotFound());
        });
    }
}
