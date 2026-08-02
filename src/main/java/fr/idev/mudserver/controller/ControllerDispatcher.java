package fr.idev.mudserver.controller;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.message.ActionNotFound;

@Component
public class ControllerDispatcher {

    private final ControllerRegistry registry;

    public ControllerDispatcher(ControllerRegistry registry) {
        this.registry = registry;
    }

    public void dispatch(Connection session, String actionName, String argument) {
        registry.find(session.state(), actionName).ifPresentOrElse(action -> action.onReceive(session, argument),
                () -> session.send(new ActionNotFound()));
    }
}
