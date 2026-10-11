package app.network;

import app.domain.Account;
import app.domain.world.WorldInstance;
import app.domain.actor.instance.PlayerInstance;

public interface Connection {

    ConnectionState state();

    void setState(ConnectionState state);

    void send(OutputMessage message);

    void close();

    // false dès que la socket sous-jacente est fermée (même si le nettoyage de fin
    // de session n'a pas encore tourné).
    boolean isOpen();

    void attachCharacter(PlayerInstance character);

    PlayerInstance character();

    void setAccount(Account account);

    Account account();

    void attachWorldInstance(WorldInstance worldInstance);
    void detachWorldInstance();

    WorldInstance worldInstance();
}
