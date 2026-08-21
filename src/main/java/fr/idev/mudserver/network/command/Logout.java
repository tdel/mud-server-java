package fr.idev.mudserver.network.command;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.WorldInstanceService;
import fr.idev.mudserver.network.CommandHandler;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.LoggedOut;
import fr.idev.mudserver.network.message.charselect.StoppedPlaying;

@Component
public class Logout implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(Logout.class);

    private final AuthWorld authWorld;
    private final WorldInstanceService worldInstanceService;

    public Logout(AuthWorld authWorld, WorldInstanceService worldInstanceService) {
        this.authWorld = authWorld;
        this.worldInstanceService = worldInstanceService;
    }

    @Override
    public String name() {
        return "logout";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.CHARSELECT, ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        Account account = connection.account();

        if (connection.state() == ConnectionState.INGAME) {
            CharacterInstance character = connection.character();
            worldInstanceService.exitGame(connection);
            connection.send(new StoppedPlaying(character.getName()));
        }

        connection.detachWorldInstance();
        authWorld.exitWorld(connection);
        log.info("auth.logged_out account={}", account.getLogin());
        connection.send(new LoggedOut());
    }
}
