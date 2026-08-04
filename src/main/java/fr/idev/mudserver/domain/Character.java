package fr.idev.mudserver.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import fr.idev.mudserver.domain.event.CharacterDroppedItem;
import fr.idev.mudserver.domain.event.CharacterEquippedItem;
import fr.idev.mudserver.domain.event.CharacterMovedToRoom;
import fr.idev.mudserver.domain.event.CharacterSpawnedToRoom;
import fr.idev.mudserver.domain.event.CharacterUnequippedItem;
import fr.idev.mudserver.domain.event.DomainEventPublisher;
import fr.idev.mudserver.domain.event.ItemPickedUp;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.OutputMessage;

/**
 * {@code connection}, {@code currentRoom} ne sont jamais persistés ni pris en
 * compte par {@link #equals}/{@link #hashCode} : ils ne représentent rien en
 * base, seulement l'état vivant du personnage tant qu'il est en jeu (voir
 * {@code GameWorld.enterWorld}). Un personnage fraîchement chargé depuis
 * {@code CharacterDao} n'a ni connexion ni room courante tant qu'il n'a pas
 * rejoint le monde via {@link #spawnToRoom} ou {@link #moveToRoom}.
 */
public class Character {

    private UUID id;
    private UUID accountId;
    private String name;
    private UUID currentRoomId;
    private Race race;
    private int level;
    private int currentHealth;
    private int maxHealth;
    private final Map<Attribute, Integer> attributes;

    private Connection connection;
    private Room currentRoom;
    private final List<Item> inventory = new CopyOnWriteArrayList<>();

    public Character(UUID id, UUID accountId, String name, UUID currentRoomId, Race race, int level, int currentHealth,
            int maxHealth, Map<Attribute, Integer> attributes) {
        this.id = id;
        this.accountId = accountId;
        this.name = name;
        this.currentRoomId = currentRoomId;
        this.race = race;
        this.level = level;
        this.currentHealth = currentHealth;
        this.maxHealth = maxHealth;
        this.attributes = new EnumMap<>(attributes);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getCurrentRoomId() {
        return currentRoomId;
    }

    public void setCurrentRoomId(UUID currentRoomId) {
        this.currentRoomId = currentRoomId;
    }

    public Race getRace() {
        return race;
    }

    public void setRace(Race race) {
        this.race = race;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getAttribute(Attribute attribute) {
        return attributes.get(attribute);
    }

    public int getModifier(Attribute attribute) {
        return Math.floorDiv(getAttribute(attribute) - 10, 2);
    }

    public int getProficiencyBonus() {
        return 2 + Math.floorDiv(level - 1, 4);
    }

    public int getCurrentHealth() {
        return currentHealth;
    }

    public void setCurrentHealth(int currentHealth) {
        this.currentHealth = currentHealth;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(int maxHealth) {
        this.maxHealth = maxHealth;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public Room getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(Room currentRoom) {
        this.currentRoom = currentRoom;
    }

    /**
     * Précondition : le personnage est déjà dans le monde, donc {@code currentRoom}
     * est déjà renseigné (voir {@link #spawnToRoom} pour l'entrée initiale, qui n'a
     * pas de room d'origine).
     */
    public void moveToRoom(Room destination) {
        Room previous = this.currentRoom;
        previous.leave(this);
        destination.join(this);
        DomainEventPublisher.publish(new CharacterMovedToRoom(this, previous, destination));
    }

    public void spawnToRoom(Room room) {
        room.join(this);
        DomainEventPublisher.publish(new CharacterSpawnedToRoom(this, room));
    }

    /**
     * Précondition : {@code item.getRoom()} désigne {@code currentRoom} — garanti
     * par {@link Room#findOneByName}, seul point d'entrée du ramassage, et par le
     * fait que tout personnage capable d'atteindre l'état {@code INGAME} a déjà
     * traversé {@link #spawnToRoom} (à la création ou à l'entrée en jeu). Suppose
     * aussi qu'il n'existe jamais qu'une seule instance JVM vivante de {@code item}
     * (cache chaud de {@code RoomService}/ {@code ItemService}, jamais rechargé par
     * requête) — sinon {@code synchronized(item)} ne synchroniserait rien entre
     * deux appels concurrents portant sur des instances différentes du même item.
     *
     * <p>
     * Deux joueurs peuvent réellement se disputer un item non possédé sous les
     * virtual threads — le verrou porte sur l'instance {@code Item} elle-même
     * plutôt que sur une ligne DB (remplace l'ancien {@code SELECT ... FOR UPDATE}
     * de {@code ItemDao#findByIdForUpdate}), la gestion des items étant désormais
     * entièrement en mémoire, la DB n'étant qu'une projection mise à jour après
     * coup via l'événement {@link ItemPickedUp}. {@code synchronized} ne bloque
     * plus (« pin ») les virtual threads sur leur carrier depuis JEP 491 (JDK 24+),
     * donc ce verrou respecte la contrainte de
     * {@code config.VirtualThreadExecutorConfig}. Le retrait de {@code currentRoom}
     * vit aussi dans le bloc verrouillé : toute la transition de possesseur (item
     * quitte la room, rejoint le personnage gagnant) reste une unité atomique face
     * à un autre {@code pickUpItem} concurrent sur le même item.
     *
     * @return true si {@code this} porte désormais l'item, false si un autre
     *         personnage l'a pris entre-temps
     */
    public boolean pickUpItem(Item item) {
        synchronized (item) {
            if (item.getCharacter() != null) {
                return false;
            }
            item.setCharacter(this);
            currentRoom.removeItem(item);
        }
        addItem(item);
        DomainEventPublisher.publish(new ItemPickedUp(this, item));
        return true;
    }

    /**
     * Aucun verrou nécessaire ici (contrairement à {@link #pickUpItem}) : un
     * personnage n'est piloté que par sa propre connexion, dont les commandes sont
     * traitées une par une, dans l'ordre, par un unique virtual thread (voir
     * {@code telnet.TelnetSessionHandler}) — deux threads ne peuvent donc jamais
     * muter l'inventaire du même personnage en même temps. Ce raisonnement suppose
     * qu'un personnage n'est jamais piloté que par une seule connexion à la fois,
     * ce qui n'est pas encore garanti pour de vrai — voir le TODO dans
     * {@code controller.connected.Login}.
     *
     * <p>
     * Publie un seul événement portant à la fois l'item équipé et l'éventuel
     * occupant précédent du même emplacement, pour que le listener persiste les
     * deux dans une même transaction — la contrainte différée
     * {@code uniq_character_slot} (V1__init_schema.sql) protège le chevauchement
     * transitoire entre les deux UPDATE au sein de cette transaction.
     */
    public Optional<EquipmentSlot> equipItem(Item item) {
        Optional<EquipmentSlot> slot = item.getType().equipmentSlot();

        if (slot.isEmpty()) {
            return Optional.empty();
        }

        List<Item> previousOccupants = new ArrayList<>();
        for (Item existing : getEquippedItems()) {
            if (!existing.getId().equals(item.getId()) && existing.getSlot() == slot.get()) {
                previousOccupants.add(existing);
                existing.setSlot(null);
            }
        }

        item.setSlot(slot.get());
        DomainEventPublisher.publish(new CharacterEquippedItem(this, item, slot.get(), previousOccupants));
        return slot;
    }

    public void unequipItem(Item item) {
        item.setSlot(null);
        DomainEventPublisher.publish(new CharacterUnequippedItem(this, item));
    }

    /**
     * Aucun verrou nécessaire (même raisonnement que {@link #equipItem}/
     * {@link #unequipItem}, voir leur Javadoc) : un personnage n'est piloté que par
     * sa propre connexion, dont les commandes sont traitées une par une par un
     * unique virtual thread. Précondition : {@code item} fait partie de
     * l'inventaire de {@code this} — garanti par {@link #findOneByName}, seul point
     * d'entrée du drop.
     */
    public void dropItem(Item item) {
        item.setRoom(currentRoom);
        removeItem(item);
        currentRoom.addItem(item);
        DomainEventPublisher.publish(new CharacterDroppedItem(this, item, currentRoom));
    }

    public List<Item> getInventory() {
        return List.copyOf(inventory);
    }

    public Optional<Item> findOneByName(String name) {
        return inventory.stream().filter(item -> item.getName().equalsIgnoreCase(name)).findFirst();
    }

    public List<Item> getCarriedItems() {
        return inventory.stream().filter(item -> item.getSlot() == null).toList();
    }

    public List<Item> getEquippedItems() {
        return inventory.stream().filter(item -> item.getSlot() != null).toList();
    }

    public void addItem(Item item) {
        inventory.add(item);
    }

    public void removeItem(Item item) {
        inventory.remove(item);
    }

    public void setInventory(List<Item> items) {
        inventory.clear();
        inventory.addAll(items);
    }

    public void send(OutputMessage message) {
        if (null != connection) {
            this.connection.send(message);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Character other)) {
            return false;
        }
        return level == other.level && currentHealth == other.currentHealth && maxHealth == other.maxHealth
                && Objects.equals(id, other.id) && Objects.equals(accountId, other.accountId)
                && Objects.equals(name, other.name) && Objects.equals(currentRoomId, other.currentRoomId)
                && race == other.race && Objects.equals(attributes, other.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, accountId, name, currentRoomId, race, level, currentHealth, maxHealth, attributes);
    }

    @Override
    public String toString() {
        return "Character[id=" + id + ", accountId=" + accountId + ", name=" + name + ", currentRoomId=" + currentRoomId
                + ", race=" + race + ", level=" + level + ", currentHealth=" + currentHealth + ", maxHealth="
                + maxHealth + ", attributes=" + attributes + "]";
    }
}
