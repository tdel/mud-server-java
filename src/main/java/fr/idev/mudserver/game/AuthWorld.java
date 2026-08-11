package fr.idev.mudserver.game;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.persistence.AccountDao;
import fr.idev.mudserver.persistence.CharacterDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.OutputMessage;

/**
 * Suit tous les clients authentifiés — compte et, une fois choisie, la
 * {@link WorldInstance} en cours — pour l'ensemble des états {@code LOBBY},
 * {@code CHARSELECT} et {@code INGAME}, jusqu'au logout ou à la déconnexion.
 * Fusionne ce qui était auparavant deux registres parallèles ({@code AuthWorld}
 * pour le compte, {@code CharacterSelectionWorld} pour l'instance) qui
 * divergeaient silencieusement l'un de l'autre : l'ancien {@code AuthWorld}
 * retirait la connexion de sa map au passage en {@code INGAME}
 * ({@code moveToGameWorld}), alors que {@code CharacterSelectionWorld} ne la
 * retirait jamais — {@code authWorld.account(connection)} devenait donc
 * injoignable en jeu alors que {@code characterSelectionWorld.worldInstance
 * (connection)} restait valide. En ne retirant plus rien avant le retour à
 * {@code CONNECTED}, {@code AuthWorld} devient la seule source de vérité "quel
 * compte / quelle instance pour cette connexion", quel que soit l'état — ce qui
 * simplifie au passage {@code controller.Logout} (plus besoin de recharger le
 * compte via {@code AccountDao} ni de le ré-enregistrer au retour en
 * {@code CHARSELECT}, {@code Connection} n'a donc pas besoin d'exposer
 * d'accesseur de compte). Le compte vit ici plutôt que sur la session
 * elle-même, sur le même principe pour l'instance : {@code Connection} n'a
 * besoin que de porter son propre personnage (voir {@code character()}), pas ce
 * qui l'entoure.
 *
 * <p>
 * Remplace le {@code SplObjectStorage} PHP par un {@link ConcurrentHashMap},
 * dont l'itérateur (voir {@link #isAlreadyConnected}) tolère un ajout/retrait
 * concurrent sans copie défensive préalable, contrairement à l'original.
 */
@Component
public class AuthWorld {

    private static final Logger log = LoggerFactory.getLogger(AuthWorld.class);

    private final Map<Connection, Account> accounts = new ConcurrentHashMap<>();
    private final Map<Connection, WorldInstance> worldInstances = new ConcurrentHashMap<>();

    private final AccountDao accountDao;
    private final CharacterDao characterDao;
    private final RoomService roomService;
    private final ItemService itemService;

    public AuthWorld(AccountDao accountDao, CharacterDao characterDao, RoomService roomService,
            ItemService itemService) {
        this.accountDao = accountDao;
        this.characterDao = characterDao;
        this.roomService = roomService;
        this.itemService = itemService;
    }

    public void enterWorld(Connection connection, Account account) {
        accounts.put(connection, account);
        connection.setState(ConnectionState.LOBBY);
        MDC.put("account", account.getLogin());
    }

    public void exitWorld(Connection connection) {
        accounts.remove(connection);
        worldInstances.remove(connection);
        connection.setState(ConnectionState.CONNECTED);
        MDC.remove("account");
    }

    /**
     * Remplace {@code CharacterSelectionWorld.enterWorld} : la connexion reste par
     * ailleurs enregistrée dans {@link #accounts} tout du long (jamais retirée par
     * cette méthode), donc {@code CharacterCreate}/{@code CharacterSelect}/
     * {@code CharacterDelete} ont simultanément accès au compte via
     * {@link #account} et à l'instance courante via {@link #worldInstance}.
     */
    public void enterWorldInstance(Connection connection, WorldInstance instance) {
        worldInstances.put(connection, instance);
        connection.setState(ConnectionState.CHARSELECT);
    }

    /**
     * Remplace {@code CharacterSelectionWorld.exitWorld}.
     */
    public void exitWorldInstance(Connection connection) {
        worldInstances.remove(connection);
        connection.setState(ConnectionState.LOBBY);
    }

    /**
     * Ne retire plus la connexion d'{@link #accounts} (contrairement au
     * comportement d'origine) : {@link #account} reste résolvable pendant tout
     * l'état {@code INGAME}, voir la Javadoc de classe. {@link #worldInstances}
     * n'est pas non plus touché ici : l'instance choisie reste celle dans laquelle
     * le personnage vient d'entrer.
     */
    public void moveToGameWorld(Connection connection, GamePlayer character) {
        Account account = accounts.get(connection);

        accountDao.updateCurrentCharacter(account.getId(), character.getId());

        connection.setState(ConnectionState.INGAME);
        enterGameWorld(connection, character);
    }

    /**
     * Câble la connexion et le personnage l'un à l'autre, charge son inventaire, le
     * fait spawn et l'enregistre dans sa {@link WorldInstance} — le seul point
     * d'entrée en jeu. Remplace {@code GameWorld.enterWorld} : ne touche ni
     * {@link #accounts} ni {@link #worldInstances} ni l'état de la connexion (déjà
     * géré par l'appelant, voir {@link #moveToGameWorld}), exactement comme
     * l'ancienne méthode.
     */
    public void enterGameWorld(Connection connection, GamePlayer character) {
        connection.setCharacter(character);
        character.setConnection(connection);
        character.getInventory().replaceItems(itemService.loadInventory(character));
        roomService.spawnCharacter(character);
        character.getWorldInstance().addPlayer(character);
        MDC.put("character", character.getName());
    }

    /**
     * Symétrique de {@link #enterGameWorld} : persiste le personnage, le déconnecte
     * de sa room et le retire de sa {@link WorldInstance}. Remplace
     * {@code GameWorld.exitWorld} ; no-op hors état {@code INGAME}, appelée
     * inconditionnellement par {@code TelnetConnection.handleClose}.
     */
    public void exitGameWorld(Connection connection) {
        if (connection.state() != ConnectionState.INGAME) {
            return;
        }

        GamePlayer character = connection.character();
        RoomInstance room = character.getCurrentRoom();
        characterDao.update(character);
        room.disconnect(character);
        character.getWorldInstance().removePlayer(character);
        log.info("character.session_ended character={} room={}", character.getName(), room.getName());
        MDC.remove("character");
    }

    public Account account(Connection connection) {
        return accounts.get(connection);
    }

    public WorldInstance worldInstance(Connection connection) {
        return worldInstances.get(connection);
    }

    /**
     * Diffuse {@code message} à toute connexion actuellement en {@code LOBBY} —
     * exclut volontairement {@code CHARSELECT}, bien que {@link #accounts} suive
     * aussi cet état (voir la Javadoc de {@link #findConnectionByLogin}).
     * Symétrique de {@code WorldInstanceService#broadcastToInstance} côté lobby.
     */
    public void broadcastToLobby(OutputMessage message, Connection exclude) {
        for (Connection connection : accounts.keySet()) {
            if (connection == exclude || connection.state() != ConnectionState.LOBBY)
                continue;
            connection.send(message);
        }
    }

    public boolean isAlreadyConnected(UUID accountId) {
        return accounts.values().stream().anyMatch(account -> account.getId().equals(accountId));
    }

    /**
     * Résout un login vers sa connexion vivante actuelle (LOBBY, CHARSELECT ou
     * désormais aussi INGAME, voir la Javadoc de classe) — utilisé par
     * {@code controller.lobby.PartyInvite} pour vérifier qu'une cible est bien
     * joignable avant de lui envoyer une invitation.
     */
    public Optional<Connection> findConnectionByLogin(String login) {
        return accounts.entrySet().stream().filter(entry -> entry.getValue().getLogin().equalsIgnoreCase(login))
                .map(Map.Entry::getKey).findFirst();
    }

    /**
     * Même principe que {@link #findConnectionByLogin}, mais par id de compte —
     * c'est la seule donnée que retient {@code domain.PartyMember}, utilisé par
     * {@code controller.lobby.WorldEnter} pour résoudre chaque membre d'une party
     * vers sa connexion courante au moment du lancement.
     */
    public Optional<Connection> findConnectionByAccountId(UUID accountId) {
        return accounts.entrySet().stream().filter(entry -> entry.getValue().getId().equals(accountId))
                .map(Map.Entry::getKey).findFirst();
    }
}
