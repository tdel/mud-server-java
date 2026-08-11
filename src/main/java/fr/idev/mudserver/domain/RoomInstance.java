package fr.idev.mudserver.domain;

import java.nio.charset.StandardCharsets;
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
 * only, never FALSE. Rooms are loaded from
 * {@code data/worlds/{monde}/rooms.json} (see {@code WorldTemplateService},
 * which validates the content, and {@code WorldInstanceService}, which
 * materializes a {@link RoomInstance} per {@link RoomTemplate}), not persisted
 * in DB, so "at most one starting room" is no longer enforced by a Postgres
 * unique index — it's validated at the application layer instead.
 *
 * <p>
 * Composition volontairement minimale : {@code id}, {@link #template} et
 * {@link #worldInstance} sont les 3 seules propriétés de configuration —
 * {@code name}/{@code description}/{@code isStartingRoom}/{@code width}/
 * {@code height}/{@code spawnCell}/{@code monsterSpawns}/portails ne sont pas
 * recopiés, chaque accesseur délègue directement à
 * {@link RoomTemplate}/{@link WorldInstance}. {@code WorldInstanceService
 * .materialize} passe la même référence {@code WorldInstance} à chaque
 * {@link RoomInstance} qu'elle construit, avant même d'appeler
 * {@link WorldInstance#setRoomInstances} — la résolution des portails (voir
 * {@link #findPortalAt}) ne lit ce graphe que plus tard, à la demande, donc
 * l'ordre de construction ne pose pas de problème.
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
 * ajout/retrait. {@link #getMonsterSpawns()} n'est pas cette liste runtime,
 * mais délègue directement à {@link RoomTemplate#getMonsterSpawns()} — la
 * config statique qui sert à peupler {@code monsters} au démarrage,
 * {@code MonsterService.loadMonsters} en consomme le contenu, ne le mute
 * jamais.
 *
 * <p>
 * {@code width}/{@code height}/{@code spawnCell} (délégués à {@link #template})
 * définissent la grille hexagonale (coordonnées axiales, cases
 * {@code q ∈ [0,width)}/{@code r ∈ [0,height)}) sur laquelle {@code occupants}
 * place chaque {@link GameCharacter} présent. Les portails (cases de bord
 * reliant cette RoomInstance à une autre, voir {@link RoomPortal}) sont résolus
 * à la demande par {@link #findPortalAt}/{@link #getPortals}, pas stockés :
 * voir leur Javadoc. {@code occupants} est volontairement clé sur la classe
 * scellée {@link GameCharacter} (pas seulement {@link GamePlayer}) pour que
 * monstres/PNJ y participent aussi — c'est le point d'accroche "interrogeable
 * par coordonnée/rayon" pour une future portée d'attaque/de ciblage, hors
 * périmètre de cette phase. Comme {@code clients}/{@code exits},
 * {@code occupants} n'est jamais persisté ni pris en compte par
 * {@link #equals}/{@link #hashCode}.
 */
public class RoomInstance {

    /**
     * Id déterministe (jamais aléatoire) d'une {@link RoomInstance} matérialisée
     * pour un couple ({@code WorldInstance}, {@code RoomTemplate}) donné — stable
     * d'un redémarrage à l'autre puisque calculé plutôt que généré, ce qui permet à
     * {@code character.current_room_id}/{@code item.room_id} de continuer à
     * désigner la bonne room après que {@code WorldInstanceService} a
     * (re)matérialisé l'instance. UUID v3 (name-based, RFC 4122) plutôt que v4 :
     * uniquement pour obtenir "même entrée ⇒ même UUID" sans état partagé entre
     * plusieurs calculs. Utilisé par {@code WorldInstanceService.materialize} et
     * par la migration {@code db.migration.V9__RecomputeDefaultInstanceItemRoomIds}
     * (qui doit reproduire exactement ce calcul en Java plutôt qu'en SQL, Postgres
     * ne répliquant pas nativement {@link UUID#nameUUIDFromBytes}).
     */
    public static UUID deterministicId(UUID worldInstanceId, UUID roomTemplateId) {
        return UUID.nameUUIDFromBytes((worldInstanceId + ":" + roomTemplateId).getBytes(StandardCharsets.UTF_8));
    }

    private final UUID id;
    private final RoomTemplate template;
    private final WorldInstance worldInstance;

    private final Map<UUID, GamePlayer> clients = new ConcurrentHashMap<>();
    private final List<Item> items = new CopyOnWriteArrayList<>();
    private final List<GameMonster> monsters = new CopyOnWriteArrayList<>();
    private final List<GameNpc> npcs = new CopyOnWriteArrayList<>();
    private final Map<HexCoordinate, GameCharacter> occupants = new ConcurrentHashMap<>();

    public RoomInstance(UUID id, RoomTemplate template, WorldInstance worldInstance) {
        this.id = id;
        this.template = template;
        this.worldInstance = worldInstance;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTemplateId() {
        return template.getId();
    }

    public WorldInstance getWorldInstance() {
        return worldInstance;
    }

    public UUID getWorldInstanceId() {
        return worldInstance.getId();
    }

    public String getName() {
        return template.getName();
    }

    public String getDescription() {
        return template.getDescription();
    }

    public Boolean isStartingRoom() {
        return template.isStartingRoom();
    }

    public int getWidth() {
        return template.getWidth();
    }

    public int getHeight() {
        return template.getHeight();
    }

    public HexCoordinate getSpawnCell() {
        return template.getSpawnCell();
    }

    public boolean isInBounds(HexCoordinate cell) {
        return template.isInBounds(cell);
    }

    public boolean isBorderCell(HexCoordinate cell) {
        return template.isBorderCell(cell);
    }

    public void join(GamePlayer character) {
        join(character, getSpawnCell());
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

    public List<MonsterSpawn> getMonsterSpawns() {
        return template.getMonsterSpawns();
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
     * Point d'entrée utilisé par {@code NpcService.warmNpcs}, pendant de
     * {@link #placeMonster} pour les PNJ.
     */
    public void placeNpc(GameNpc npc, HexCoordinate cell) {
        addNpc(npc);
        npc.setPosition(claimNearestFreeCell(cell, npc));
    }

    public Optional<GameNpc> findNpcByName(String name) {
        return npcs.stream().filter(npc -> npc.getName().equalsIgnoreCase(name)).findFirst();
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

    /**
     * Résolus à la demande depuis {@link RoomTemplate#getPortals()} (les
     * {@link RoomTemplatePortal}, cibles par id de {@code RoomTemplate}) plutôt que
     * stockés : {@link #worldInstance} porte le graphe de {@link RoomInstance}
     * sœurs (voir {@link WorldInstance#roomInstanceForTemplate}), déjà complet au
     * moment où un joueur peut effectivement interroger un portail (voir la Javadoc
     * de tête de classe sur l'ordre de construction de
     * {@code WorldInstanceService.materialize}).
     */
    public List<RoomPortal> getPortals() {
        return template.getPortals().stream().map(this::toRoomPortal).toList();
    }

    public Optional<RoomPortal> findPortalAt(HexCoordinate cell) {
        return template.getPortals().stream().filter(portal -> portal.cell().equals(cell)).findFirst()
                .map(this::toRoomPortal);
    }

    private RoomPortal toRoomPortal(RoomTemplatePortal portal) {
        RoomInstance target = worldInstance.roomInstanceForTemplate(portal.targetRoomTemplateId())
                .orElseThrow(() -> new IllegalStateException("Portail de " + id + " vers "
                        + portal.targetRoomTemplateId() + ", absente de " + worldInstance.getId()));
        return new RoomPortal(portal.cell(), portal.direction(), this, target, portal.targetCell());
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
        if (!(o instanceof RoomInstance other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "RoomInstance[id=" + id + ", templateId=" + template.getId() + ", worldInstanceId="
                + worldInstance.getId() + "]";
    }
}
