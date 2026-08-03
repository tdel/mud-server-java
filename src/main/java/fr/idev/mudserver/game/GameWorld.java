package fr.idev.mudserver.game;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.persistence.CharacterDao;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.network.Connection;

/**
 * Suit tous les joueurs actuellement dans le monde de jeu, pour toute la durée
 * de vie du process, et la session associée à chacun — sur le même principe que
 * {@link AuthWorld} : {@code Connection} n'a donc pas besoin d'exposer
 * d'accesseur de personnage.
 */
@Component
public class GameWorld {

    private final Map<Connection, Character> characters = new ConcurrentHashMap<>();

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
     * {@code Character#spawnToRoom}) — même principe que
     * {@code itemService.loadInventory(character)} juste au-dessus : le service
     * résout tout à partir du {@code Character}, {@code GameWorld} ne manipule
     * jamais d'UUID directement.
     */
    public void enterWorld(Connection connection, Character character) {
        character.setConnection(connection);
        character.setInventory(itemService.loadInventory(character));
        characters.put(connection, character);
        roomService.spawnCharacter(character);
    }

    public void exitWorld(Connection connection) {
        Character character = characters.remove(connection);
        if (character == null) {
            return;
        }

        characterDao.update(character);
        character.getCurrentRoom().disconnect(character);
    }

    public Character character(Connection connection) {
        return characters.get(connection);
    }

    public boolean isCharacterInGame(UUID characterId) {
        return characters.values().stream().anyMatch(character -> character.getId().equals(characterId));
    }

    public boolean isAlreadyConnected(UUID accountId) {
        return characters.values().stream().anyMatch(character -> character.getAccountId().equals(accountId));
    }
}
