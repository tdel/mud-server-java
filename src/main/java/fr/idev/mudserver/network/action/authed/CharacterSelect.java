package fr.idev.mudserver.network.action.authed;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.game.AuthWorld;
import fr.idev.mudserver.game.GameWorld;
import fr.idev.mudserver.game.PlayerInstance;
import fr.idev.mudserver.network.ActionHandler;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.Session;
import fr.idev.mudserver.network.action.ingame.Look;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.authed.NoCharacterNamed;
import fr.idev.mudserver.network.message.authed.NowPlaying;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;

@Component
public class CharacterSelect implements ActionHandler {

    private final CharacterDao characterDao;
    private final AccountDao accountDao;
    private final AuthWorld authWorld;
    private final GameWorld gameWorld;
    private final CharacterList characterListAction;
    private final Look lookAction;

    public CharacterSelect(CharacterDao characterDao, AccountDao accountDao, AuthWorld authWorld, GameWorld gameWorld,
            CharacterList characterListAction, Look lookAction) {
        this.characterDao = characterDao;
        this.accountDao = accountDao;
        this.authWorld = authWorld;
        this.gameWorld = gameWorld;
        this.characterListAction = characterListAction;
        this.lookAction = lookAction;
    }

    @Override
    public String name() {
        return "character-select";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.AUTHED);
    }

    @Override
    public void onReceive(Session session, String argument) {
        String name = argument.trim();

        if (name.isEmpty()) {
            session.send(new Usage("character-select <name>"));
            characterListAction.onReceive(session, "");
            return;
        }

        Account account = session.account();

        Optional<Character> character = characterDao.findByAccountIdAndName(account.id(), name);
        if (character.isEmpty()) {
            session.send(new NoCharacterNamed(name));
            characterListAction.onReceive(session, "");
            return;
        }

        accountDao.updateCurrentCharacter(account.id(), character.get().id());

        // détache de l'AuthWorld, attache la PlayerInstance à la session, fait passer
        // l'état
        // à INGAME, puis fait rejoindre GameWorld (broadcast d'arrivée dans la room).
        authWorld.moveToGameWorld(session);
        PlayerInstance player = new PlayerInstance(session, character.get());
        session.setState(ConnectionState.INGAME);
        gameWorld.enterWorld(player);

        session.send(new NowPlaying(character.get().name()));
        lookAction.onReceive(session, "");
    }
}
