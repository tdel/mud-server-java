package fr.idev.mudserver.game;

import fr.idev.mudserver.domain.*;
import fr.idev.mudserver.domain.actor.GamePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.event.NewGamePlayerCreated;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.persistence.CharacterDao;

/**
 * Point d'entrée/sortie unique du jeu — ne suit plus lui-même "qui est en jeu"
 * : cette responsabilité vit désormais sur {@link WorldInstance#addPlayer}/
 * {@link WorldInstance#removePlayer} (voir sa Javadoc) et sur
 * {@code Connection.character()} (voir {@code network.Connection}), chacun
 * scopé à sa propre frontière naturelle plutôt que centralisé ici dans une
 * seule {@code Map<Connection, GamePlayer>} pour tout le process.
 */
@Component
public class GameWorld {

    private static final Logger log = LoggerFactory.getLogger(GameWorld.class);

    private final CharacterDao characterDao;
    private final RoomService roomService;
    private final ItemService itemService;

    public GameWorld(CharacterDao characterDao, RoomService roomService, ItemService itemService) {
        this.characterDao = characterDao;
        this.roomService = roomService;
        this.itemService = itemService;
    }

    /**
     * Délègue la résolution de la room de départ à
     * {@link RoomService#spawnCharacter} : un personnage qui vient d'être chargé
     * depuis {@code CharacterDao} n'a que son {@code currentRoomId} persistée
     * ({@code character.getCurrentRoom()} n'est renseigné qu'en effet de bord de
     * {@code GamePlayer#spawnToRoom}) — même principe que
     * {@code itemService.loadInventory(character)} juste au-dessus : le service
     * résout tout à partir du {@code GamePlayer}, {@code GameWorld} ne manipule
     * jamais d'UUID directement. {@code character.getWorldInstance()} n'est
     * renseigné qu'en effet de bord de {@link RoomService#spawnCharacter} (via
     * {@code WorldInstanceService.spawnCharacterIntoInstance}) : l'enregistrement
     * dans {@link WorldInstance#addPlayer} doit donc avoir lieu après cet appel,
     * pas avant.
     */
    public void enterWorld(Connection connection, GamePlayer character) {
        connection.setCharacter(character);
        character.setConnection(connection);
        character.getInventory().replaceItems(itemService.loadInventory(character));
        roomService.spawnCharacter(character);
        character.getWorldInstance().addPlayer(character);
        MDC.put("character", character.getName());
    }

    /**
     * Idempotent et sans effet hors état {@code INGAME} : appelé sans condition par
     * {@code TelnetConnection.handleClose} quel que soit l'état réel de la
     * connexion au moment de la déconnexion.
     */
    public void exitWorld(Connection connection) {
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

    @EventListener
    void onNewGamePlayerCreated(NewGamePlayerCreated event) {
        characterDao.insert(event.character());
        log.info("character.created character={} accountId={} race={} class={}", event.character().getName(),
                event.character().getAccountId(), event.character().getRace(), event.character().getCharacterClass());
    }
}
