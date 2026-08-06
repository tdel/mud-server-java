package fr.idev.mudserver.domain.actor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.event.CharacterGainedXp;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerDroppedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerEquippedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerMovedToRoom;
import fr.idev.mudserver.domain.actor.event.GamePlayerSpawnedToRoom;
import fr.idev.mudserver.domain.actor.event.GamePlayerUnequippedItem;
import fr.idev.mudserver.domain.actor.event.ItemPickedUp;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.domain.RoomPortal;
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
    private Gender gender;
    private Race race;
    private CharacterClass characterClass;
    private final Set<Attribute> savingThrowProficiencies;
    private final Set<Skill> skillProficiencies;
    private int level;

    private Connection connection;
    private final PlayerInventory inventory;
    private GameMonster target;
    private int xp;

    public GamePlayer(UUID id, UUID accountId, String name, UUID currentRoomId, Gender gender, Race race,
            CharacterClass characterClass, Set<Attribute> savingThrowProficiencies, Set<Skill> skillProficiencies,
            int level, int currentHealth, int maxHealth, Map<Attribute, Integer> attributes, int xp, int gold) {
        super(id, name, attributes, currentHealth, maxHealth);
        this.accountId = accountId;
        this.currentRoomId = currentRoomId;
        this.gender = gender;
        this.race = race;
        this.characterClass = characterClass;
        this.savingThrowProficiencies = Set.copyOf(savingThrowProficiencies);
        this.skillProficiencies = Set.copyOf(skillProficiencies);
        this.level = level;
        this.xp = xp;
        this.inventory = new PlayerInventory(gold);
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

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
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

    public Set<Attribute> getSavingThrowProficiencies() {
        return savingThrowProficiencies;
    }

    public Set<Skill> getSkillProficiencies() {
        return skillProficiencies;
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

    @Override
    public int getArmorClass() {
        int ac = inventory.getEquippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.CHEST).findFirst()
                .map(this::armorAc).orElseGet(super::getArmorClass);

        return ac + inventory.getEquippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.OFF_HAND)
                .mapToInt(Item::getBaseAc).sum();
    }

    private int armorAc(Item armor) {
        int dexMod = getModifier(Attribute.DEXTERITY);
        return switch (armor.getArmorCategory()) {
            case LIGHT -> armor.getBaseAc() + dexMod;
            case MEDIUM -> armor.getBaseAc() + Math.min(dexMod, 2);
            case HEAVY -> armor.getBaseAc();
        };
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public GameMonster getTarget() {
        return target;
    }

    public void setTarget(GameMonster target) {
        this.target = target;
    }

    public int getXp() {
        return xp;
    }

    /**
     * Seul point d'entrée pour muter l'XP — pas de setter public, sur le même
     * principe que {@link #pickUpItem}/{@link #equipItem} : la mutation publie
     * toujours {@link CharacterGainedXp}, dont le listener (voir
     * {@code game.actor.CharacterService}) décide d'un éventuel passage de niveau,
     * hors de portée d'un simple POJO sans accès à {@code LevelService}/
     * {@code ClassService}.
     */
    public void gainXp(int amount) {
        this.xp += amount;
        DomainEventPublisher.publish(new CharacterGainedXp(this, amount));
    }

    /**
     * Contrairement à {@link GameMonster#takeDamage}, aucun verrou propre n'est
     * nécessaire ici : un joueur n'appartient jamais qu'à un seul
     * {@link CombatEncounter} à la fois (la règle de fusion de
     * {@code game.CombatEngine} refuse un second affrontement concurrent), et toute
     * mutation de PV liée au combat n'a lieu que pendant qu'un thread détient déjà
     * le verrou de cet affrontement (son propre tour, ou la riposte d'un monstre
     * pendant la cascade) — la sérialisation est donc héritée de ce verrou-là,
     * transitivement, plutôt que portée par cette méthode elle-même. Publie
     * {@link GamePlayerDied} sur le coup fatal, pendant côté joueur de
     * {@link GameMonster#takeDamage}.
     *
     * @return true si ce coup est celui qui a fait passer les PV à 0 ou moins
     */
    public boolean takeDamage(int amount, GameMonster attacker) {
        if (getCurrentHealth() <= 0) {
            return false;
        }
        setCurrentHealth(Math.max(0, getCurrentHealth() - amount));
        boolean defeated = getCurrentHealth() <= 0;
        if (defeated) {
            DomainEventPublisher.publish(new GamePlayerDied(this, attacker));
        }
        return defeated;
    }

    /**
     * Précondition : le personnage est déjà dans le monde, donc {@code currentRoom}
     * est déjà renseigné (voir {@link #spawnToRoom} pour l'entrée initiale, qui n'a
     * pas de room d'origine). Place le personnage sur la case de spawn de
     * {@code destination} — utilisé par la restauration après la mort
     * ({@code CharacterService#onGamePlayerDied}), où il n'y a pas de case cible de
     * portail à respecter.
     */
    public void moveToRoom(Room destination) {
        moveToRoom(destination, destination.getSpawnCell());
    }

    /**
     * Variante utilisée par {@link #crossPortal} lorsqu'un déplacement case par
     * case fait franchir un {@code RoomPortal} : {@code targetCell} est la case
     * cible du portail plutôt que la case de spawn de la room.
     */
    public void moveToRoom(Room destination, HexCoordinate targetCell) {
        Room previous = getCurrentRoom();
        previous.leave(this);
        destination.join(this, targetCell);
        DomainEventPublisher.publish(new GamePlayerMovedToRoom(this, previous, destination));
    }

    /**
     * Seul sous-type à réellement traverser un {@link RoomPortal} rencontré par
     * {@link #moveToCell} : {@link GameMonster}/{@link GameNpc} restent sur la
     * case-portail (voir la base {@code GameCharacter#crossPortal}).
     */
    @Override
    protected boolean crossPortal(RoomPortal portal) {
        moveToRoom(portal.targetRoom(), portal.targetCell());
        return true;
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
        inventory.addItem(item);
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
        for (Item existing : inventory.getEquippedItems()) {
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
        inventory.removeItem(item);
        currentRoom.addItem(item);
        DomainEventPublisher.publish(new GamePlayerDroppedItem(this, item, currentRoom));
    }

    public PlayerInventory getInventory() {
        return inventory;
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
        return level == other.level && xp == other.xp && inventory.getGold() == other.inventory.getGold()
                && getCurrentHealth() == other.getCurrentHealth() && getMaxHealth() == other.getMaxHealth()
                && Objects.equals(getId(), other.getId()) && Objects.equals(accountId, other.accountId)
                && Objects.equals(getName(), other.getName()) && Objects.equals(currentRoomId, other.currentRoomId)
                && gender == other.gender && race == other.race && characterClass == other.characterClass
                && Objects.equals(getAttributes(), other.getAttributes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), accountId, getName(), currentRoomId, gender, race, characterClass, level, xp,
                inventory.getGold(), getCurrentHealth(), getMaxHealth(), getAttributes());
    }

    @Override
    public String toString() {
        return "GamePlayer[id=" + getId() + ", accountId=" + accountId + ", name=" + getName() + ", currentRoomId="
                + currentRoomId + ", gender=" + gender + ", race=" + race + ", characterClass=" + characterClass
                + ", level=" + level + ", xp=" + xp + ", gold=" + inventory.getGold() + ", currentHealth="
                + getCurrentHealth() + ", maxHealth=" + getMaxHealth() + ", attributes=" + getAttributes() + "]";
    }
}
