package fr.idev.mudserver.network;

import java.util.function.Consumer;

public interface Connection {

    void requestBlocking(OutputMessage message, Consumer<String> handler);

    ConnectionState state();

    void setState(ConnectionState state);

    void send(OutputMessage message);

    void close();
}
