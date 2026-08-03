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

    public GameWorld(CharacterDao characterDao, RoomService roomService) {
        this.characterDao = characterDao;
        this.roomService = roomService;
    }

    public void enterWorld(Connection connection, Character character) {
        character.setConnection(connection);
        characters.put(connection, character);
        roomService.moveCharacter(character, character.getCurrentRoomId());
    }

    public void exitWorld(Connection connection) {
        Character character = characters.remove(connection);
        if (character == null) {
            return;
        }

        characterDao.update(character);
        roomService.room(character.getCurrentRoomId()).disconnect(character);
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
