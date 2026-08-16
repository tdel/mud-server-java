package fr.idev.mudserver.network;

import java.util.function.Consumer;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.world.WorldInstance;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

public interface Connection {

    void requestBlocking(OutputMessage message, Consumer<String> handler);

    ConnectionState state();

    void setState(ConnectionState state);

    void send(OutputMessage message);

    void close();

    void attachCharacter(CharacterInstance character);
    void detachCharacter();

    CharacterInstance character();

    void setAccount(Account account);

    Account account();

    void attachWorldInstance(WorldInstance worldInstance);
    void detachWorldInstance();

    WorldInstance worldInstance();
}
