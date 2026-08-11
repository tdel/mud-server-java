package fr.idev.mudserver.game;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.domain.*;
import fr.idev.mudserver.domain.actor.GamePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.actor.event.NewGamePlayerCreated;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.persistence.CharacterDao;

/**
 * Suit tous les joueurs actuellement dans le monde de jeu, pour toute la durée
 * de vie du process, et la session associée à chacun — sur le même principe que
 * {@link AuthWorld} : {@code Connection} n'a donc pas besoin d'exposer
 * d'accesseur de personnage.
 */
@Component
public class GameWorld {

    private static final Logger log = LoggerFactory.getLogger(GameWorld.class);

    private final Map<Connection, GamePlayer> characters = new ConcurrentHashMap<>();

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
     * jamais d'UUID directement.
     */
    public void enterWorld(Connection connection, GamePlayer character) {
        character.setConnection(connection);
        character.getInventory().replaceItems(itemService.loadInventory(character));
        characters.put(connection, character);
        roomService.spawnCharacter(character);
        MDC.put("character", character.getName());
    }

    public void exitWorld(Connection connection) {
        GamePlayer character = characters.remove(connection);
        if (character == null) {
            return;
        }

        RoomInstance room = character.getCurrentRoom();
        characterDao.update(character);
        character.getCurrentRoom().disconnect(character);
        log.info("character.session_ended character={} room={}", character.getName(), room.getName());
        MDC.remove("character");
    }

    public GamePlayer character(Connection connection) {
        return characters.get(connection);
    }

    /**
     * Tous les joueurs actuellement en jeu, consommé par
     * {@code game.actor.RestService} : un repos court/long affecte l'ensemble des
     * joueurs en ligne, pas seulement celui qui l'initie (voir sa Javadoc).
     */
    public Collection<GamePlayer> onlineCharacters() {
        return List.copyOf(characters.values());
    }

    /**
     * Sous-ensemble de {@link #onlineCharacters()} scopé à une
     * {@code WorldInstance} — consommé par {@code game.actor.RestService} : un
     * repos court/long affecte tous les joueurs en ligne de l'instance de
     * l'initiateur, plus le process entier (voir {@code multi-world.md} Phase E).
     */
    public Collection<GamePlayer> onlineCharactersInWorldInstance(UUID worldInstanceId) {
        return characters.values().stream().filter(character -> worldInstanceId.equals(character.getWorldInstanceId()))
                .toList();
    }

    public boolean isCharacterInGame(UUID characterId) {
        return characters.values().stream().anyMatch(character -> character.getId().equals(characterId));
    }

    public boolean isAlreadyConnected(UUID accountId) {
        return characters.values().stream().anyMatch(character -> character.getAccountId().equals(accountId));
    }

    @EventListener
    void onNewGamePlayerCreated(NewGamePlayerCreated event) {
        characterDao.insert(event.character());
        log.info("character.created character={} accountId={} race={} class={}", event.character().getName(),
                event.character().getAccountId(), event.character().getRace(), event.character().getCharacterClass());
    }
}
