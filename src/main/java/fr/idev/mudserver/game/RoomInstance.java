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
    private final Set<Client> clients = ConcurrentHashMap.newKeySet();

    RoomInstance(UUID roomId) {
        this.roomId = roomId;
    }

    public void join(Client client) {
        client.character().setCurrentRoomId(roomId);
        clients.add(client);
        broadcast(new CharacterJoinedRoom(client.character().getName()), client);
    }

    public void leave(Client client, Room destination) {
        clients.remove(client);
        broadcast(new CharacterLeftRoom(client.character().getName(), destination.getName()), client);
    }

    public void disconnect(Client client) {
        clients.remove(client);
        broadcast(new CharacterDisconnected(client.character().getName()), client);
    }

    public Character findCharacterByName(String name) {
        for (Client client : clients) {
            if (client.character().getName().equalsIgnoreCase(name)) {
                return client.character();
            }
        }
        return null;
    }

    public List<Character> characters() {
        List<Character> characters = new ArrayList<>();
        for (Client client : clients) {
            characters.add(client.character());
        }
        return characters;
    }

    public void broadcast(OutputMessage message, Client exclude) {
        for (Client client : clients) {
            if (client == exclude) {
                continue;
            }
            client.send(message);
        }
    }
}
