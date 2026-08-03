package fr.idev.mudserver.game;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.Connection;

public class Client {

    private final Connection session;
    private final Character character;

    public Client(Connection session, Character character) {
        this.session = session;
        this.character = character;
    }

    public Character character() {
        return character;
    }

    public void send(OutputMessage message) {
        session.send(message);
    }
}
