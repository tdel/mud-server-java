package app.network;

import java.util.function.Consumer;

import app.domain.Account;
import app.domain.world.WorldInstance;
import app.domain.actor.instance.CharacterInstance;

public interface Connection {

    void requestBlocking(OutputMessage message, Consumer<String> handler);

    ConnectionState state();

    void setState(ConnectionState state);

    void send(OutputMessage message);

    void close();

    void attachCharacter(CharacterInstance character);

    CharacterInstance character();

    void setAccount(Account account);

    Account account();

    void attachWorldInstance(WorldInstance worldInstance);
    void detachWorldInstance();

    WorldInstance worldInstance();
}
