package fr.idev.mudserver.network;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.message.ActionNotFound;

@Component
public class ActionDispatcher {

    private final ActionRegistry registry;

    public ActionDispatcher(ActionRegistry registry) {
        this.registry = registry;
    }

    public void dispatch(Session session, String actionName, String argument) {
        registry.find(session.state(), actionName).ifPresentOrElse(action -> action.onReceive(session, argument),
                () -> session.send(new ActionNotFound()));
    }
}
