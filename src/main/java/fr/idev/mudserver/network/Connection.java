package fr.idev.mudserver.network;

import java.util.function.Consumer;

import fr.idev.mudserver.game.PlayerInstance;

public interface Connection {

    void requestBlocking(OutputMessage message, Consumer<String> handler);

    PlayerInstance player();

    void attachPlayer(PlayerInstance player);

    ConnectionState state();

    void setState(ConnectionState state);

    void send(OutputMessage message);

    void close();
}
