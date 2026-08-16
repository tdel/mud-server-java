package fr.idev.mudserver.domain.actor.instance;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.*;
import fr.idev.mudserver.domain.actor.component.*;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerEnteredCell;
import fr.idev.mudserver.domain.actor.event.GamePlayerMovedToRoom;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.actor.system.AttributeSystem;
import fr.idev.mudserver.domain.actor.system.InventorySystem;
import fr.idev.mudserver.domain.world.RoomInstance;
import fr.idev.mudserver.domain.item.WeaponCategory;
import fr.idev.mudserver.domain.world.WorldInstance;
import fr.idev.mudserver.game.dice.CheckResult;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.OutputMessage;

public final class CharacterInstance extends AbstractCharacter {

    private final Account account;
    private UUID worldInstanceId;
    private WorldInstance worldInstance;
    private Gender gender;
    private Race race;
    private CharacterClass characterClass;

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
        super(id, name, attributes, currentHealth, maxHealth, race.speed());
        this.account = account;
        setCurrentRoom(room);
        this.gender = gender;
        this.race = race;
        this.characterClass = characterClass;
        attachComponent(new InventoryComponent(List.of(), gold));
        attachComponent(new LevelingComponent(level, xp));
        attachComponent(new RestComponent(shortRestCount));
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

    public int getProficiencyBonus() {
        return 2 + Math.floorDiv(component(LevelingComponent.class).level() - 1, 4);
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
        int modifier = AttributeSystem.getModifier(this, attribute) + (proficient ? getProficiencyBonus() : 0);
        boolean disadvantage = (attribute == Attribute.STRENGTH || attribute == Attribute.DEXTERITY)
                && InventorySystem.isWearingNonProficientArmor(this);
        DiceRoll diceRoll = DiceRoller.rollD20(modifier, disadvantage);
        boolean success = diceRoll.total() >= dc;
        return new CheckResult(label, diceRoll.total(), dc, proficient, disadvantage, success);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CharacterInstance other)) {
            return false;
        }
        LevelingComponent level = component(LevelingComponent.class);
        LevelingComponent otherLevel = other.component(LevelingComponent.class);
        InventoryComponent inventory = component(InventoryComponent.class);
        InventoryComponent otherInventory = other.component(InventoryComponent.class);
        RestComponent rest = component(RestComponent.class);
        RestComponent otherRest = other.component(RestComponent.class);
        return level.level() == otherLevel.level() && level.xp() == otherLevel.xp()
                && inventory.gold() == otherInventory.gold() && getCurrentHealth() == other.getCurrentHealth()
                && getMaxHealth() == other.getMaxHealth() && rest.shortRestCount() == otherRest.shortRestCount()
                && Objects.equals(getId(), other.getId()) && Objects.equals(getAccountId(), other.getAccountId())
                && Objects.equals(getName(), other.getName())
                && Objects.equals(getCurrentRoomId(), other.getCurrentRoomId()) && gender == other.gender
                && race == other.race && characterClass == other.characterClass
                && Objects.equals(AttributeSystem.getAttributes(this), AttributeSystem.getAttributes(other));
    }

    @Override
    public int hashCode() {
        LevelingComponent level = component(LevelingComponent.class);
        InventoryComponent inventory = component(InventoryComponent.class);
        RestComponent rest = component(RestComponent.class);
        return Objects.hash(getId(), getAccountId(), getName(), getCurrentRoomId(), gender, race, characterClass,
                level.level(), level.xp(), inventory.gold(), getCurrentHealth(), getMaxHealth(), rest.shortRestCount(),
                AttributeSystem.getAttributes(this));
    }

    @Override
    public String toString() {
        LevelingComponent level = component(LevelingComponent.class);
        InventoryComponent inventory = component(InventoryComponent.class);
        RestComponent rest = component(RestComponent.class);
        return "GamePlayer[id=" + getId() + ", accountId=" + getAccountId() + ", name=" + getName() + ", currentRoomId="
                + getCurrentRoomId() + ", gender=" + gender + ", race=" + race + ", characterClass=" + characterClass
                + ", level=" + level.level() + ", xp=" + level.xp() + ", gold=" + inventory.gold() + ", currentHealth="
                + getCurrentHealth() + ", maxHealth=" + getMaxHealth() + ", shortRestCount=" + rest.shortRestCount()
                + ", attributes=" + AttributeSystem.getAttributes(this) + "]";
    }
}
