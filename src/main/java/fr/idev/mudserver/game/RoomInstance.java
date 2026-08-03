package fr.idev.mudserver.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.message.ingame.CharacterDisconnected;
import fr.idev.mudserver.network.message.ingame.CharacterJoinedRoom;
import fr.idev.mudserver.network.message.ingame.CharacterLeftRoom;

/**
 * Les joueurs actuellement présents dans une room, et le point d'entrée pour
 * diffuser un message à tout le monde dedans. Rejoindre/quitter notifie
 * toujours la room, l'appelant n'a jamais besoin d'y penser lui-même.
 *
 * <p>
 * {@code ConcurrentHashMap.newKeySet()} remplace le {@code SplObjectStorage}
 * PHP — son itérateur est faiblement cohérent (jamais de
 * {@code ConcurrentModificationException}, tolère un ajout/retrait pendant
 * l'itération), donc {@link #broadcast} n'a pas besoin du snapshot défensif que
 * fait la version PHP avant d'itérer.
 */
public class RoomInstance {

    private final UUID roomId;
    private final Map<Connection, Character> clients = new ConcurrentHashMap<>();

    RoomInstance(UUID roomId) {
        this.roomId = roomId;
    }

    public void join(Connection connection, Character character) {
        character.setCurrentRoomId(roomId);
        clients.put(connection, character);
        broadcast(new CharacterJoinedRoom(character.getName()), connection);
    }

    public void leave(Connection connection, Character character, Room destination) {
        clients.remove(connection);
        broadcast(new CharacterLeftRoom(character.getName(), destination.getName()), connection);
    }

    public void disconnect(Connection connection, Character character) {
        clients.remove(connection);
        broadcast(new CharacterDisconnected(character.getName()), connection);
    }

    public Character findCharacterByName(String name) {
        for (Character character : clients.values()) {
            if (character.getName().equalsIgnoreCase(name)) {
                return character;
            }
        }
        return null;
    }

    public List<Character> characters() {
        return new ArrayList<>(clients.values());
    }

    public void broadcast(OutputMessage message, Connection exclude) {
        for (Map.Entry<Connection, Character> entry : clients.entrySet()) {
            if (entry.getKey() == exclude) {
                continue;
            }
            entry.getKey().send(message);
        }
    }
}
