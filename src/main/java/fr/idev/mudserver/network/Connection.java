package fr.idev.mudserver.network;

import java.util.function.Consumer;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.domain.actor.GamePlayer;

public interface Connection {

    void requestBlocking(OutputMessage message, Consumer<String> handler);

    ConnectionState state();

    void setState(ConnectionState state);

    void send(OutputMessage message);

    void close();

    void setCharacter(GamePlayer character);

    GamePlayer character();

    void setAccount(Account account);

    Account account();

    void setWorldInstance(WorldInstance worldInstance);

    WorldInstance worldInstance();
}
