package fr.idev.mudserver.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Room;
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
    private final Set<PlayerInstance> players = ConcurrentHashMap.newKeySet();

    RoomInstance(UUID roomId) {
        this.roomId = roomId;
    }

    public void join(PlayerInstance player) {
        player.moveToRoom(roomId);
        players.add(player);
        broadcast(new CharacterJoinedRoom(player.character().getName()), player);
    }

    public void leave(PlayerInstance player, Room destination) {
        players.remove(player);
        broadcast(new CharacterLeftRoom(player.character().getName(), destination.getName()), player);
    }

    public void disconnect(PlayerInstance player) {
        players.remove(player);
        broadcast(new CharacterDisconnected(player.character().getName()), player);
    }

    public Character findCharacterByName(String name) {
        for (PlayerInstance player : players) {
            if (player.character().getName().equalsIgnoreCase(name)) {
                return player.character();
            }
        }
        return null;
    }

    public List<Character> characters() {
        List<Character> characters = new ArrayList<>();
        for (PlayerInstance player : players) {
            characters.add(player.character());
        }
        return characters;
    }

    public void broadcast(OutputMessage message, PlayerInstance exclude) {
        for (PlayerInstance player : players) {
            if (player == exclude) {
                continue;
            }
            player.send(message);
        }
    }
}
