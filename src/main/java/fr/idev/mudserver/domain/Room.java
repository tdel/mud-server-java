package fr.idev.mudserver.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import fr.idev.mudserver.domain.actor.GameCharacter;
import fr.idev.mudserver.domain.actor.GameMonster;
import fr.idev.mudserver.domain.actor.GameNpc;
import fr.idev.mudserver.domain.actor.GamePlayer;
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
 *
 * <p>
 * {@code width}/{@code height}/{@code spawnCell} définissent la grille
 * hexagonale (coordonnées axiales, cases {@code q ∈ [0,width)}/
 * {@code r ∈ [0,height)}) sur laquelle {@code occupants} place chaque
 * {@link GameCharacter} présent, et {@code portals} les cases de bord reliant
 * cette Room à une autre (voir {@link RoomPortal}). {@code occupants} est
 * volontairement clé sur la classe scellée {@link GameCharacter} (pas seulement
 * {@link GamePlayer}) pour que monstres/PNJ y participent aussi — c'est le
 * point d'accroche "interrogeable par coordonnée/rayon" pour une future portée
 * d'attaque/de ciblage, hors périmètre de cette phase. Comme
 * {@code clients}/{@code exits}, {@code occupants}/{@code portals} ne sont
 * jamais persistés ni pris en compte par {@link #equals}/{@link #hashCode}.
 */
public class Room {

    public static final int DEFAULT_WIDTH = 16;
    public static final int DEFAULT_HEIGHT = 8;

    private UUID id;
    private String name;
    private String description;
    private Boolean isStartingRoom;
    private int width;
    private int height;
    private HexCoordinate spawnCell;

    private final Map<UUID, GamePlayer> clients = new ConcurrentHashMap<>();
    private final List<Item> items = new CopyOnWriteArrayList<>();
    private final List<GameMonster> monsters = new CopyOnWriteArrayList<>();
    private final List<GameNpc> npcs = new CopyOnWriteArrayList<>();
    private final Map<HexCoordinate, GameCharacter> occupants = new ConcurrentHashMap<>();
    private final Map<HexCoordinate, RoomPortal> portals = new ConcurrentHashMap<>();

    public Room(UUID id, String name, String description, Boolean isStartingRoom) {
        this(id, name, description, isStartingRoom, DEFAULT_WIDTH, DEFAULT_HEIGHT,
                new HexCoordinate(DEFAULT_WIDTH / 2, DEFAULT_HEIGHT / 2));
    }

    public Room(UUID id, String name, String description, Boolean isStartingRoom, int width, int height,
            HexCoordinate spawnCell) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.isStartingRoom = isStartingRoom;
        this.width = width;
        this.height = height;
        this.spawnCell = spawnCell;
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

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public HexCoordinate getSpawnCell() {
        return spawnCell;
    }

    public boolean isInBounds(HexCoordinate cell) {
        return cell.q() >= 0 && cell.q() < width && cell.r() >= 0 && cell.r() < height;
    }

    public boolean isBorderCell(HexCoordinate cell) {
        return isInBounds(cell) && (cell.q() == 0 || cell.q() == width - 1 || cell.r() == 0 || cell.r() == height - 1);
    }

    public void join(GamePlayer character) {
        join(character, spawnCell);
    }

    public void join(GamePlayer character, HexCoordinate cell) {
        character.setCurrentRoom(this);
        character.setPosition(claimNearestFreeCell(cell, character));
        clients.put(character.getId(), character);
        broadcast(new GamePlayerJoinedRoom(character.getName()), character);
    }

    public void leave(GamePlayer character) {
        clients.remove(character.getId());
        releaseCell(character.getPosition(), character);
        character.setPosition(null);
        broadcast(new GamePlayerLeftRoom(character.getName()), character);
    }

    public void disconnect(GamePlayer character) {
        clients.remove(character.getId());
        releaseCell(character.getPosition(), character);
        character.setPosition(null);
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
        List<GameCharacter> occupantsByName = new ArrayList<>();
        occupantsByName.addAll(clients.values());
        occupantsByName.addAll(monsters);
        occupantsByName.addAll(npcs);
        return occupantsByName.stream().filter(occupant -> occupant.getName().equalsIgnoreCase(name)).findFirst();
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
        releaseCell(monster.getPosition(), monster);
    }

    public Optional<GameMonster> findMonsterByName(String name) {
        return monsters.stream().filter(monster -> monster.getName().equalsIgnoreCase(name)).findFirst();
    }

    public void setMonsters(List<GameMonster> monsters) {
        this.monsters.clear();
        this.monsters.addAll(monsters);
    }

    /**
     * Point d'entrée utilisé par {@code MonsterService.loadMonsters} : ajoute le
     * monstre à la liste (comme {@link #addMonster}) et réclame sa case de spawn,
     * pour qu'il participe dès le démarrage à {@link #occupantAt}/
     * {@link #occupantsWithin}.
     */
    public void placeMonster(GameMonster monster, HexCoordinate cell) {
        addMonster(monster);
        monster.setPosition(claimNearestFreeCell(cell, monster));
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

    /**
     * Point d'entrée utilisé par {@code NpcService.loadNpcs}, pendant de
     * {@link #placeMonster} pour les PNJ.
     */
    public void placeNpc(GameNpc npc, HexCoordinate cell) {
        addNpc(npc);
        npc.setPosition(claimNearestFreeCell(cell, npc));
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

    public List<RoomPortal> getPortals() {
        return List.copyOf(portals.values());
    }

    public void setPortals(List<RoomPortal> portalList) {
        portals.clear();
        for (RoomPortal portal : portalList) {
            portals.put(portal.cell(), portal);
        }
    }

    public Optional<RoomPortal> findPortalAt(HexCoordinate cell) {
        return Optional.ofNullable(portals.get(cell));
    }

    public Optional<GameCharacter> occupantAt(HexCoordinate cell) {
        return Optional.ofNullable(occupants.get(cell));
    }

    /**
     * Interrogation par coordonnée/rayon utilisée aujourd'hui par le viewport de
     * {@code Look} — c'est le point d'accroche explicite pour une future portée
     * d'attaque/de ciblage, non conçue dans cette phase.
     */
    public List<GameCharacter> occupantsWithin(HexCoordinate center, int radius) {
        return center.withinRadius(radius).stream().map(occupants::get).filter(Objects::nonNull).toList();
    }

    /**
     * Réclamation atomique d'une case, analogue au {@code synchronized(item)} de
     * {@code GamePlayer#pickUpItem} (voir CLAUDE.md) mais portée directement par
     * l'opération atomique {@code putIfAbsent} de {@link ConcurrentHashMap} : deux
     * virtual threads visant la même case ne peuvent jamais tous les deux gagner.
     */
    public boolean tryClaimCell(HexCoordinate cell, GameCharacter character) {
        return occupants.putIfAbsent(cell, character) == null;
    }

    public void releaseCell(HexCoordinate cell, GameCharacter character) {
        if (cell != null) {
            occupants.remove(cell, character);
        }
    }

    /**
     * Réclame {@code desired} si libre, sinon cherche par cercles croissants (rayon
     * borné à 3) la case libre la plus proche — utile pour un spawn bondé ou une
     * case cible de portail déjà occupée. En dernier recours (aucune case libre
     * trouvée dans ce rayon), place quand même sur {@code desired} : l'invariant
     * "une case = un occupant" n'est alors plus qu'un défaut fort, pas une garantie
     * stricte — à surveiller en plaçant les PNJ/monstres loin des cases de spawn.
     */
    private HexCoordinate claimNearestFreeCell(HexCoordinate desired, GameCharacter character) {
        if (tryClaimCell(desired, character)) {
            return desired;
        }
        List<HexCoordinate> candidates = desired.withinRadius(3).stream()
                .filter(cell -> !cell.equals(desired) && isInBounds(cell))
                .sorted(Comparator.comparingInt(desired::distanceTo)).toList();
        for (HexCoordinate candidate : candidates) {
            if (tryClaimCell(candidate, character)) {
                return candidate;
            }
        }
        return desired;
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
        return width == other.width && height == other.height && Objects.equals(id, other.id)
                && Objects.equals(name, other.name) && Objects.equals(description, other.description)
                && Objects.equals(isStartingRoom, other.isStartingRoom) && Objects.equals(spawnCell, other.spawnCell);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, isStartingRoom, width, height, spawnCell);
    }

    @Override
    public String toString() {
        return "Room[id=" + id + ", name=" + name + ", description=" + description + ", isStartingRoom="
                + isStartingRoom + ", width=" + width + ", height=" + height + ", spawnCell=" + spawnCell + "]";
    }
}
