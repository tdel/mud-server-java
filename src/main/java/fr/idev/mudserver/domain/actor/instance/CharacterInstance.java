package fr.idev.mudserver.domain.actor.instance;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.*;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerEnteredCell;
import fr.idev.mudserver.domain.actor.event.GamePlayerMovedToRoom;
import fr.idev.mudserver.domain.actor.system.LevelingSystem;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.actor.system.InventorySystem;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.item.WeaponCategory;
import fr.idev.mudserver.domain.world.WorldInstance;
import fr.idev.mudserver.game.dice.CheckResult;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.OutputMessage;

public final class CharacterInstance extends AbstractCharacter {

    private final Account account;
    private UUID worldInstanceId;
    private WorldInstance worldInstance;
    private Gender gender;
    private Race race;
    private CharacterClass characterClass;

    private Connection connection;
    private MonsterInstance target;

    public static final int MAX_SHORT_RESTS_BEFORE_LONG_REST = 2;

    public CharacterInstance(UUID id, Account account, String name, RoomInstance room, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold) {
        this(id, account, name, room, gender, race, characterClass, level, currentHealth, maxHealth, attributes, xp,
                gold, 0);
    }

    public CharacterInstance(UUID id, Account account, String name, RoomInstance room, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold, int shortRestCount) {
        super(id, name, attributes, currentHealth, maxHealth);
        this.account = account;
        setCurrentRoom(room);
        this.gender = gender;
        this.race = race;
        this.speed = race.speed();
        this.characterClass = characterClass;
        InventorySystem.attach(this, gold);
        LevelingSystem.attach(this, level, xp, shortRestCount);
    }

    public Account getAccount() {
        return account;
    }

    public UUID getAccountId() {
        return account.getId();
    }

    public UUID getCurrentRoomId() {
        return getCurrentRoom().getTemplateId();
    }

    public UUID getWorldInstanceId() {
        return worldInstanceId;
    }

    public void setWorldInstanceId(UUID worldInstanceId) {
        this.worldInstanceId = worldInstanceId;
    }

    public WorldInstance getWorldInstance() {
        return worldInstance;
    }

    public void setWorldInstance(WorldInstance worldInstance) {
        this.worldInstance = worldInstance;
        this.worldInstanceId = worldInstance.getId();
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

    public Attribute getPrimaryAbility() {
        return characterClass.primaryAbility();
    }

    public Set<Attribute> getSavingThrowProficiencies() {
        return characterClass.savingThrowProficiencies();
    }

    public Set<Skill> getSkillProficiencies() {
        return characterClass.skillProficiencies();
    }

    public Set<WeaponCategory> getWeaponProficiencies() {
        return characterClass.weaponProficiencies();
    }

    public Set<ArmorProficiency> getArmorProficiencies() {
        return characterClass.armorProficiencies();
    }

    public int getLevel() {
        return LevelingSystem.level(this);
    }

    public int getProficiencyBonus() {
        return 2 + Math.floorDiv(getLevel() - 1, 4);
    }

    public CheckResult check(Skill skill, int dc) {
        boolean proficient = getSkillProficiencies().contains(skill);
        return checkOrSave(skill.getGoverningAttribute(), proficient, dc, skill.label());
    }

    public CheckResult save(Attribute attribute, int dc) {
        boolean proficient = getSavingThrowProficiencies().contains(attribute);
        return checkOrSave(attribute, proficient, dc, attribute.label());
    }

    private CheckResult checkOrSave(Attribute attribute, boolean proficient, int dc, String label) {
        int modifier = getModifier(attribute) + (proficient ? getProficiencyBonus() : 0);
        boolean disadvantage = (attribute == Attribute.STRENGTH || attribute == Attribute.DEXTERITY)
                && InventorySystem.isWearingNonProficientArmor(this);
        DiceRoll diceRoll = DiceRoller.rollD20(modifier, disadvantage);
        boolean success = diceRoll.total() >= dc;
        return new CheckResult(label, diceRoll.total(), dc, proficient, disadvantage, success);
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public MonsterInstance getTarget() {
        return target;
    }

    public void setTarget(MonsterInstance target) {
        this.target = target;
    }

    public int getXp() {
        return LevelingSystem.xp(this);
    }

    public int getShortRestCount() {
        return LevelingSystem.shortRestCount(this);
    }

    public void moveToRoom(RoomInstance destination) {
        moveToRoom(destination, destination.getSpawnCell());
    }

    public void moveToRoom(RoomInstance destination, HexCoordinate targetCell) {
        RoomInstance previous = getCurrentRoom();
        previous.leave(this);
        destination.join(this, targetCell);
        DomainEventPublisher.publish(new GamePlayerMovedToRoom(this, previous, destination));
    }

    @Override
    public boolean onEnteredCell(HexCoordinate cell) {
        if (isInCombat()) {
            return false;
        }
        DomainEventPublisher.publish(new GamePlayerEnteredCell(this, cell));
        return isInCombat();
    }

    public int getGold() {
        return InventorySystem.gold(this);
    }

    public List<Item> getItems() {
        return InventorySystem.items(this);
    }

    public List<Item> getCarriedItems() {
        return InventorySystem.carriedItems(this);
    }

    public Optional<Item> findOneByName(String name) {
        return InventorySystem.findOneByName(this, name);
    }

    @Override
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
        if (!(o instanceof CharacterInstance other)) {
            return false;
        }
        return getLevel() == other.getLevel() && getXp() == other.getXp() && getGold() == other.getGold()
                && getCurrentHealth() == other.getCurrentHealth() && getMaxHealth() == other.getMaxHealth()
                && getShortRestCount() == other.getShortRestCount() && Objects.equals(getId(), other.getId())
                && Objects.equals(getAccountId(), other.getAccountId()) && Objects.equals(getName(), other.getName())
                && Objects.equals(getCurrentRoomId(), other.getCurrentRoomId()) && gender == other.gender
                && race == other.race && characterClass == other.characterClass
                && Objects.equals(getAttributes(), other.getAttributes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getAccountId(), getName(), getCurrentRoomId(), gender, race, characterClass,
                getLevel(), getXp(), getGold(), getCurrentHealth(), getMaxHealth(), getShortRestCount(),
                getAttributes());
    }

    @Override
    public String toString() {
        return "GamePlayer[id=" + getId() + ", accountId=" + getAccountId() + ", name=" + getName() + ", currentRoomId="
                + getCurrentRoomId() + ", gender=" + gender + ", race=" + race + ", characterClass=" + characterClass
                + ", level=" + getLevel() + ", xp=" + getXp() + ", gold=" + getGold() + ", currentHealth="
                + getCurrentHealth() + ", maxHealth=" + getMaxHealth() + ", shortRestCount=" + getShortRestCount()
                + ", attributes=" + getAttributes() + "]";
    }
}
