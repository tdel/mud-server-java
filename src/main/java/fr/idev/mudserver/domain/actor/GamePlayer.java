package fr.idev.mudserver.domain.actor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.actor.event.CharacterGainedXp;
import fr.idev.mudserver.domain.actor.event.CharacterLootedItem;
import fr.idev.mudserver.domain.actor.event.CharacterReceivedGold;
import fr.idev.mudserver.domain.actor.event.CharacterSpentGold;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerEnteredCell;
import fr.idev.mudserver.domain.actor.event.GamePlayerEquippedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerMovedToRoom;
import fr.idev.mudserver.domain.actor.event.GamePlayerSpawnedToRoom;
import fr.idev.mudserver.domain.actor.event.GamePlayerUnequippedItem;
import fr.idev.mudserver.domain.actor.event.ItemDiscarded;
import fr.idev.mudserver.domain.actor.event.ItemPurchased;
import fr.idev.mudserver.domain.EquipmentSlot;
import fr.idev.mudserver.domain.HexCoordinate;
import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.WeaponCategory;
import fr.idev.mudserver.domain.WorldInstance;
import fr.idev.mudserver.game.CombatResult;
import fr.idev.mudserver.game.dice.CheckResult;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.OutputMessage;

public final class GamePlayer extends GameCharacter {

    private final Account account;
    private UUID worldInstanceId;
    private WorldInstance worldInstance;
    private Gender gender;
    private Race race;
    private CharacterClass characterClass;
    private int level;

    private Connection connection;
    private final PlayerInventory inventory;
    private GameMonster target;
    private int xp;
    private int shortRestCount;

    public static final int MAX_SHORT_RESTS_BEFORE_LONG_REST = 2;

    public GamePlayer(UUID id, Account account, String name, RoomInstance room, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold) {
        this(id, account, name, room, gender, race, characterClass, level, currentHealth, maxHealth, attributes, xp,
                gold, 0);
    }

    public GamePlayer(UUID id, Account account, String name, RoomInstance room, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold, int shortRestCount) {
        super(id, name, attributes, currentHealth, maxHealth);
        this.account = account;
        setCurrentRoom(room);
        this.gender = gender;
        this.race = race;
        this.speed = race.speed();
        this.characterClass = characterClass;
        this.level = level;
        this.xp = xp;
        this.inventory = new PlayerInventory(gold);
        this.shortRestCount = shortRestCount;
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
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getProficiencyBonus() {
        return 2 + Math.floorDiv(level - 1, 4);
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
                && isWearingNonProficientArmor();
        DiceRoll diceRoll = DiceRoller.rollD20(modifier, disadvantage);
        boolean success = diceRoll.total() >= dc;
        return new CheckResult(label, diceRoll.total(), dc, proficient, disadvantage, success);
    }

    @Override
    public int getArmorClass() {
        int ac = inventory.getEquippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.CHEST).findFirst()
                .map(this::armorAc).orElseGet(super::getArmorClass);

        return ac + inventory.getEquippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.OFF_HAND)
                .mapToInt(item -> item.getBaseAc() + item.getBonus()).sum();
    }

    private int armorAc(Item armor) {
        int dexMod = getModifier(Attribute.DEXTERITY);
        int baseAndBonus = armor.getBaseAc() + armor.getBonus();
        return switch (armor.getArmorCategory()) {
            case LIGHT -> baseAndBonus + dexMod;
            case MEDIUM -> baseAndBonus + Math.min(dexMod, 2);
            case HEAVY -> baseAndBonus;
        };
    }

    public boolean isWearingNonProficientArmor() {
        return inventory.getEquippedItems().stream().map(this::requiredArmorProficiency)
                .anyMatch(required -> required.isPresent() && !getArmorProficiencies().contains(required.get()));
    }

    private Optional<ArmorProficiency> requiredArmorProficiency(Item item) {
        return switch (item.getType()) {
            case ARMOR, HELMET, PANTS, BOOTS, GLOVES -> Optional.of(ArmorProficiency.of(item.getArmorCategory()));
            case SHIELD -> Optional.of(ArmorProficiency.SHIELDS);
            default -> Optional.empty();
        };
    }

    public CombatResult tryAttack(GameMonster target) {
        Optional<Item> weapon = inventory.getEquippedItems().stream()
                .filter(item -> item.getSlot() == EquipmentSlot.WEAPON).findFirst();
        int weaponBonus = weapon.map(Item::getBonus).orElse(0);
        boolean weaponProficient = weapon.map(item -> getWeaponProficiencies().contains(item.getWeaponCategory()))
                .orElse(true);

        int strengthModifier = getModifier(Attribute.STRENGTH);
        int attackBonus = strengthModifier + (weaponProficient ? getProficiencyBonus() : 0) + weaponBonus;
        boolean disadvantage = isWearingNonProficientArmor();

        DiceRoll attackRoll = DiceRoller.rollD20(attackBonus, disadvantage);
        int naturalRoll = attackRoll.rolls()[0];
        boolean criticalHit = naturalRoll == 20;
        int armorClass = target.getArmorClass();
        boolean hit = DiceRoller.resolveHit(naturalRoll, attackRoll.total(), armorClass);

        if (!hit) {
            return new CombatResult(target.getName(), false, false, attackRoll.total(), armorClass, 0, disadvantage);
        }

        int damage = rollDamage(weapon, strengthModifier, criticalHit);
        return new CombatResult(target.getName(), true, criticalHit, attackRoll.total(), armorClass, damage,
                disadvantage);
    }

    private int rollDamage(Optional<Item> weapon, int strengthModifier, boolean criticalHit) {
        if (weapon.isEmpty()) {
            // Attaque à mains nues (SRD) : 1 + modificateur de FOR, pas de dé donc rien à
            // doubler en cas de critique.
            return Math.max(0, 1 + strengthModifier);
        }

        DiceExpression base = DiceExpression.parse(weapon.get().getDamageDice());
        int diceCount = criticalHit ? base.count() * 2 : base.count();
        int modifier = strengthModifier + weapon.get().getBonus();
        return Math.max(0, DiceRoller.roll(new DiceExpression(diceCount, base.sides(), modifier)).total());
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

    public void gainXp(int amount) {
        this.xp += amount;
        DomainEventPublisher.publish(new CharacterGainedXp(this, amount));
    }

    public int getShortRestCount() {
        return shortRestCount;
    }

    public boolean canTakeShortRest() {
        return shortRestCount < MAX_SHORT_RESTS_BEFORE_LONG_REST;
    }

    public void incrementShortRestCount() {
        shortRestCount++;
    }

    public void resetShortRestCount() {
        shortRestCount = 0;
    }

    public void receiveGold(int amount) {
        inventory.addGold(amount);
        DomainEventPublisher.publish(new CharacterReceivedGold(this, amount));
    }

    public void receiveLootItem(Item item) {
        item.setCharacter(this);
        inventory.addItem(item);
        DomainEventPublisher.publish(new CharacterLootedItem(this, item));
    }

    public boolean buyItem(Item item, int price) {
        if (!inventory.trySpendGold(price)) {
            return false;
        }
        DomainEventPublisher.publish(new CharacterSpentGold(this, price));
        item.setCharacter(this);
        inventory.addItem(item);
        DomainEventPublisher.publish(new ItemPurchased(this, item, price));
        return true;
    }

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

    public void spawnToRoom(RoomInstance room) {
        room.join(this);
        DomainEventPublisher.publish(new GamePlayerSpawnedToRoom(this, room));
    }

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

    public void discardItem(Item item) {
        inventory.removeItem(item);
        DomainEventPublisher.publish(new ItemDiscarded(this, item));
    }

    public PlayerInventory getInventory() {
        return inventory;
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
        if (!(o instanceof GamePlayer other)) {
            return false;
        }
        return level == other.level && xp == other.xp && inventory.getGold() == other.inventory.getGold()
                && getCurrentHealth() == other.getCurrentHealth() && getMaxHealth() == other.getMaxHealth()
                && shortRestCount == other.shortRestCount && Objects.equals(getId(), other.getId())
                && Objects.equals(getAccountId(), other.getAccountId()) && Objects.equals(getName(), other.getName())
                && Objects.equals(getCurrentRoomId(), other.getCurrentRoomId()) && gender == other.gender
                && race == other.race && characterClass == other.characterClass
                && Objects.equals(getAttributes(), other.getAttributes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getAccountId(), getName(), getCurrentRoomId(), gender, race, characterClass, level,
                xp, inventory.getGold(), getCurrentHealth(), getMaxHealth(), shortRestCount, getAttributes());
    }

    @Override
    public String toString() {
        return "GamePlayer[id=" + getId() + ", accountId=" + getAccountId() + ", name=" + getName() + ", currentRoomId="
                + getCurrentRoomId() + ", gender=" + gender + ", race=" + race + ", characterClass=" + characterClass
                + ", level=" + level + ", xp=" + xp + ", gold=" + inventory.getGold() + ", currentHealth="
                + getCurrentHealth() + ", maxHealth=" + getMaxHealth() + ", shortRestCount=" + shortRestCount
                + ", attributes=" + getAttributes() + "]";
    }
}
