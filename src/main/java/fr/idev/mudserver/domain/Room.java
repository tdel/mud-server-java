package fr.idev.mudserver.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.message.ingame.GamePlayerDisconnected;
import fr.idev.mudserver.network.message.ingame.GamePlayerJoinedRoom;
import fr.idev.mudserver.network.message.ingame.GamePlayerLeftRoom;

/**
 * {@code isStartingRoom} is a nullable sentinel, not a plain boolean: NULL/TRUE
 * only, never FALSE. Rooms are loaded from {@code data/rooms.json} (see
 * {@code RoomService.warmRooms()}), not persisted in DB, so "at most one
 * starting room" is no longer enforced by a Postgres unique index — it's
 * validated at the application layer in {@code RoomService.loadRooms()}
 * instead.
 *
 * <p>
 * {@code clients} suit les joueurs actuellement présents dans la room, et sert
 * de point d'entrée pour diffuser un message à tout le monde dedans.
 * Rejoindre/quitter notifie toujours la room, l'appelant n'a jamais besoin d'y
 * penser lui-même. {@code ConcurrentHashMap} remplace le
 * {@code SplObjectStorage} PHP — son itérateur est faiblement cohérent (jamais
 * de {@code ConcurrentModificationException}, tolère un ajout/retrait pendant
 * l'itération), donc {@link #broadcast} n'a pas besoin du snapshot défensif que
 * fait la version PHP avant d'itérer. Ce champ n'est jamais persisté ni pris en
 * compte par {@link #equals}/{@link #hashCode} : il ne représente rien en base,
 * uniquement l'état vivant du process. Clé sur l'id du personnage plutôt que sa
 * connexion : {@link GamePlayer} porte désormais sa propre connexion.
 *
 * <p>
 * {@code monsters}/{@code npcs} suivent de la même façon les monstres/PNJ
 * placés dans la room au démarrage (voir {@code MonsterService.warmMonsters}/
 * {@code NpcService.warmNpcs}) — contrairement à {@code clients}, ils ne
 * rejoignent/quittent jamais dynamiquement dans ce périmètre (pas d'IA, pas de
 * déplacement), donc pas de notification {@code broadcast} à leur
 * ajout/retrait.
 */
public class Room {

    private UUID id;
    private String name;
    private String description;
    private Boolean isStartingRoom;

    private final Map<UUID, GamePlayer> clients = new ConcurrentHashMap<>();
    private final List<Item> items = new CopyOnWriteArrayList<>();
    private final List<RoomExit> exits = new CopyOnWriteArrayList<>();
    private final List<GameMonster> monsters = new CopyOnWriteArrayList<>();
    private final List<GameNpc> npcs = new CopyOnWriteArrayList<>();

    public Room(UUID id, String name, String description, Boolean isStartingRoom) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.isStartingRoom = isStartingRoom;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean isStartingRoom() {
        return isStartingRoom;
    }

    public void setStartingRoom(Boolean startingRoom) {
        this.isStartingRoom = startingRoom;
    }

    public void join(GamePlayer character) {
        character.setCurrentRoom(this);
        clients.put(character.getId(), character);
        broadcast(new GamePlayerJoinedRoom(character.getName()), character);
    }

    public void leave(GamePlayer character) {
        clients.remove(character.getId());
        broadcast(new GamePlayerLeftRoom(character.getName()), character);
    }

    public void disconnect(GamePlayer character) {
        clients.remove(character.getId());
        broadcast(new GamePlayerDisconnected(character.getName()), character);
    }

    /**
     * Point d'entrée unique pour « qui s'appelle X dans cette room », quel que soit
     * son type concret — {@link GameCharacter} est scellée
     * ({@link GamePlayer}/{@link GameMonster}/{@link GameNpc}), ce qui permet à un
     * appelant (voir {@code Examine}) de faire un {@code switch} exhaustif sur le
     * résultat sans clause {@code default}.
     */
    public Optional<GameCharacter> findOccupantByName(String name) {
        List<GameCharacter> occupants = new ArrayList<>();
        occupants.addAll(clients.values());
        occupants.addAll(monsters);
        occupants.addAll(npcs);
        return occupants.stream().filter(occupant -> occupant.getName().equalsIgnoreCase(name)).findFirst();
    }

    public List<GamePlayer> characters() {
        return new ArrayList<>(clients.values());
    }

    public List<GameMonster> getMonsters() {
        return List.copyOf(monsters);
    }

    public void addMonster(GameMonster monster) {
        monsters.add(monster);
    }

    public void removeMonster(GameMonster monster) {
        monsters.remove(monster);
    }

    public Optional<GameMonster> findMonsterByName(String name) {
        return monsters.stream().filter(monster -> monster.getName().equalsIgnoreCase(name)).findFirst();
    }

    public void setMonsters(List<GameMonster> monsters) {
        this.monsters.clear();
        this.monsters.addAll(monsters);
    }

    public List<GameNpc> getNpcs() {
        return List.copyOf(npcs);
    }

    public void addNpc(GameNpc npc) {
        npcs.add(npc);
    }

    public void setNpcs(List<GameNpc> npcs) {
        this.npcs.clear();
        this.npcs.addAll(npcs);
    }

    public List<Item> getItems() {
        return List.copyOf(items);
    }

    public Optional<Item> findOneByName(String name) {
        return items.stream().filter(item -> item.getName().equalsIgnoreCase(name)).findFirst();
    }

    public void addItem(Item item) {
        items.add(item);
    }

    public void removeItem(Item item) {
        items.remove(item);
    }

    public void setItems(List<Item> items) {
        this.items.clear();
        this.items.addAll(items);
    }

    public List<RoomExit> getExits() {
        return List.copyOf(exits);
    }

    public void setExits(List<RoomExit> exits) {
        this.exits.clear();
        this.exits.addAll(exits);
    }

    public Optional<RoomExit> findOneByDirection(String direction) {
        return exits.stream().filter(exit -> exit.getDirection().equals(direction)).findFirst();
    }

    public void broadcast(OutputMessage message, GamePlayer exclude) {
        for (GamePlayer character : clients.values()) {
            if (character == exclude || character.getConnection() == null) {
                continue;
            }
            character.getConnection().send(message);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Room other)) {
            return false;
        }
        return Objects.equals(id, other.id) && Objects.equals(name, other.name)
                && Objects.equals(description, other.description)
                && Objects.equals(isStartingRoom, other.isStartingRoom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, isStartingRoom);
    }

    @Override
    public String toString() {
        return "Room[id=" + id + ", name=" + name + ", description=" + description + ", isStartingRoom="
                + isStartingRoom + "]";
    }
}
