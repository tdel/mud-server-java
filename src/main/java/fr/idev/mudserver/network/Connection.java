package fr.idev.mudserver.network;

import java.util.function.Consumer;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.game.PlayerInstance;

public interface Connection {

    void requestBlocking(OutputMessage message, Consumer<String> handler);

    Account account();

    void attachAccount(Account account);

    PlayerInstance player();

    void attachPlayer(PlayerInstance player);

    ConnectionState state();

    void setState(ConnectionState state);

    void send(OutputMessage message);

    void promptMasked(OutputMessage message, Consumer<String> onLine);

    void close();
}
