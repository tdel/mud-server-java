package fr.idev.mudserver.network.action.connected;

import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.network.ActionHandler;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.Session;
import fr.idev.mudserver.network.message.connected.Goodbye;

@Component
public class Quit implements ActionHandler {

    @Override
    public String name() {
        return "quit";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.CONNECTED);
    }

    @Override
    public void onReceive(Session session, String argument) {
        session.send(new Goodbye());
        session.close();
    }
}
