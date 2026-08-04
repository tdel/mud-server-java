package fr.idev.mudserver.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import fr.idev.mudserver.domain.event.DomainEventPublisher;
import fr.idev.mudserver.domain.event.GamePlayerDroppedItem;
import fr.idev.mudserver.domain.event.GamePlayerEquippedItem;
import fr.idev.mudserver.domain.event.GamePlayerMovedToRoom;
import fr.idev.mudserver.domain.event.GamePlayerSpawnedToRoom;
import fr.idev.mudserver.domain.event.GamePlayerUnequippedItem;
import fr.idev.mudserver.domain.event.ItemPickedUp;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.OutputMessage;

/**
 * {@code connection} n'est jamais persisté ni pris en compte par
 * {@link #equals}/{@link #hashCode} : il ne représente rien en base, seulement
 * l'état vivant du personnage tant qu'il est en jeu (voir
 * {@code GameWorld.enterWorld}) — même convention pour {@code currentRoom},
 * porté par {@link GameCharacter}. Un personnage fraîchement chargé depuis
 * {@code CharacterDao} n'a ni connexion ni room courante tant qu'il n'a pas
 * rejoint le monde via {@link #spawnToRoom} ou {@link #moveToRoom}.
 */
public final class GamePlayer extends GameCharacter {

    private UUID accountId;
    private UUID currentRoomId;
    private Race race;
    private CharacterClass characterClass;
    private int level;

    private Connection connection;
    private final List<Item> inventory = new CopyOnWriteArrayList<>();

    public GamePlayer(UUID id, UUID accountId, String name, UUID currentRoomId, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes) {
        super(id, name, attributes, currentHealth, maxHealth);
        this.accountId = accountId;
        this.currentRoomId = currentRoomId;
        this.race = race;
        this.characterClass = characterClass;
        this.level = level;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
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

    public CharacterClass getCharacterClass() {
        return characterClass;
    }

    public void setCharacterClass(CharacterClass characterClass) {
        this.characterClass = characterClass;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getProficiencyBonus() {
        return 2 + Math.floorDiv(level - 1, 4);
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    /**
     * Précondition : le personnage est déjà dans le monde, donc {@code currentRoom}
     * est déjà renseigné (voir {@link #spawnToRoom} pour l'entrée initiale, qui n'a
     * pas de room d'origine).
     */
    public void moveToRoom(Room destination) {
        Room previous = getCurrentRoom();
        previous.leave(this);
        destination.join(this);
        DomainEventPublisher.publish(new GamePlayerMovedToRoom(this, previous, destination));
    }

    public void spawnToRoom(Room room) {
        room.join(this);
        DomainEventPublisher.publish(new GamePlayerSpawnedToRoom(this, room));
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
            getCurrentRoom().removeItem(item);
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
        DomainEventPublisher.publish(new GamePlayerEquippedItem(this, item, slot.get(), previousOccupants));
        return slot;
    }

    public void unequipItem(Item item) {
        item.setSlot(null);
        DomainEventPublisher.publish(new GamePlayerUnequippedItem(this, item));
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
        Room currentRoom = getCurrentRoom();
        item.setRoom(currentRoom);
        removeItem(item);
        currentRoom.addItem(item);
        DomainEventPublisher.publish(new GamePlayerDroppedItem(this, item, currentRoom));
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
        if (!(o instanceof GamePlayer other)) {
            return false;
        }
        return level == other.level && getCurrentHealth() == other.getCurrentHealth()
                && getMaxHealth() == other.getMaxHealth() && Objects.equals(getId(), other.getId())
                && Objects.equals(accountId, other.accountId) && Objects.equals(getName(), other.getName())
                && Objects.equals(currentRoomId, other.currentRoomId) && race == other.race
                && characterClass == other.characterClass && Objects.equals(getAttributes(), other.getAttributes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), accountId, getName(), currentRoomId, race, characterClass, level,
                getCurrentHealth(), getMaxHealth(), getAttributes());
    }

    @Override
    public String toString() {
        return "GamePlayer[id=" + getId() + ", accountId=" + accountId + ", name=" + getName() + ", currentRoomId="
                + currentRoomId + ", race=" + race + ", characterClass=" + characterClass + ", level=" + level
                + ", currentHealth=" + getCurrentHealth() + ", maxHealth=" + getMaxHealth() + ", attributes="
                + getAttributes() + "]";
    }
}
