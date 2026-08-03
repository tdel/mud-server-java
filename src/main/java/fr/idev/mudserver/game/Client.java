package fr.idev.mudserver.game;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.Connection;

public class Client {

    private final Connection connection;
    private final Character character;

    public Client(Connection connection, Character character) {
        this.connection = connection;
        this.character = character;
    }

    public Character character() {
        return character;
    }

    public void send(OutputMessage message) {
        connection.send(message);
    }
}
