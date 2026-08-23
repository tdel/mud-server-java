package fr.idev.mudserver.domain.actor.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import fr.idev.mudserver.domain.Account;
import fr.idev.mudserver.domain.Spell;
import fr.idev.mudserver.domain.actor.*;
import fr.idev.mudserver.domain.actor.component.ActiveEffect;
import fr.idev.mudserver.domain.actor.component.CharacterCombat;
import fr.idev.mudserver.domain.actor.component.PlayerInventory;
import fr.idev.mudserver.domain.actor.component.SpellCasting;
import fr.idev.mudserver.domain.actor.event.CharacterGainedXp;
import fr.idev.mudserver.domain.actor.event.CharacterLeveledUp;
import fr.idev.mudserver.domain.actor.event.CharacterLootedItem;
import fr.idev.mudserver.domain.actor.event.CharacterReceivedGold;
import fr.idev.mudserver.domain.actor.event.CharacterRegenerated;
import fr.idev.mudserver.domain.actor.event.CharacterSpentGold;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.event.GamePlayerDamaged;
import fr.idev.mudserver.domain.actor.event.GamePlayerDied;
import fr.idev.mudserver.domain.actor.event.GamePlayerEquippedItem;
import fr.idev.mudserver.domain.actor.event.GamePlayerMovedToZone;
import fr.idev.mudserver.domain.actor.event.GamePlayerUnequippedItem;
import fr.idev.mudserver.domain.actor.event.ItemDiscarded;
import fr.idev.mudserver.domain.actor.event.ItemPurchased;
import fr.idev.mudserver.domain.actor.event.SpellCast;
import fr.idev.mudserver.domain.item.EquipmentSlot;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.world.ZoneInstance;
import fr.idev.mudserver.domain.item.WeaponCategory;
import fr.idev.mudserver.domain.world.WorldInstance;
import fr.idev.mudserver.game.dice.CheckResult;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.game.dice.DiceRoller;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.OutputMessage;

public final class CharacterInstance extends AbstractCharacter {

    private final Account account;
    private WorldInstance worldInstance;
    private Gender gender;
    private Race race;
    private CharacterClass characterClass;
    private int level;

    private Connection connection;
    private final PlayerInventory inventory;
    private final CharacterCombat combat;
    private int xp;
    private int shortRestCount;
    private int maxMana;
    private int currentMana;

    public static final int MAX_SHORT_RESTS_BEFORE_LONG_REST = 2;

    public CharacterInstance(UUID id, Account account, String name, ZoneInstance zone, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold) {
        this(id, account, name, zone, gender, race, characterClass, level, currentHealth, maxHealth, attributes, xp,
                gold, 0, 0, 0);
    }

    public CharacterInstance(UUID id, Account account, String name, ZoneInstance zone, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold, int shortRestCount, int maxMana, int currentMana) {
        this(id, account, name, zone, gender, race, characterClass, level, currentHealth, maxHealth, attributes, xp,
                gold, shortRestCount, maxMana, currentMana, Set.of(), List.of());
    }

    public CharacterInstance(UUID id, Account account, String name, ZoneInstance zone, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold, int shortRestCount, int maxMana, int currentMana,
            Set<Spell> knownSpells, List<ActiveEffect> activeEffects) {
        super(id, name, attributes, currentHealth, maxHealth);
        this.account = account;
        setCurrentZone(zone);
        this.gender = gender;
        this.race = race;
        this.speed = race.speed();
        this.characterClass = characterClass;
        this.level = level;
        this.xp = xp;
        this.inventory = new PlayerInventory(gold);
        this.combat = new CharacterCombat(this);
        this.shortRestCount = shortRestCount;
        this.maxMana = maxMana;
        this.currentMana = currentMana;
        knownSpells.forEach(getSpellCasting()::learn);
        activeEffects.forEach(getActiveEffects()::apply);
    }

    public Account getAccount() {
        return account;
    }

    public UUID getAccountId() {
        return account.getId();
    }

    public UUID getCurrentZoneId() {
        return getCurrentZone().getTemplateId();
    }

    public WorldInstance getWorldInstance() {
        return worldInstance;
    }

    public void setWorldInstance(WorldInstance worldInstance) {
        this.worldInstance = worldInstance;
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

    @Override
    public int getSpellAttackBonus() {
        return getProficiencyBonus() + getModifier(characterClass.primaryAbility());
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

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public CharacterCombat getCombat() {
        return combat;
    }

    public Set<Spell> getGrantedSpells() {
        return inventory.getEquippedItems().stream().flatMap(item -> item.getTemplate().getGrantedSpells().stream())
                .collect(Collectors.toSet());
    }

    public int getMaxMana() {
        return maxMana;
    }

    public void setMaxMana(int maxMana) {
        this.maxMana = maxMana;
    }

    public int getCurrentMana() {
        return currentMana;
    }

    public void setCurrentMana(int currentMana) {
        this.currentMana = currentMana;
    }

    public boolean trySpendMana(int amount) {
        if (currentMana < amount) {
            return false;
        }
        currentMana -= amount;
        return true;
    }

    public int gainMana(int amount) {
        int gained = Math.min(amount, maxMana - currentMana);
        currentMana += gained;
        return gained;
    }

    public int healthRegenAmountPerTick() {
        return 1 + level / 5;
    }

    public int manaRegenAmountPerTick() {
        return Math.max(0, 1 + getModifier(characterClass.primaryAbility()));
    }

    public void regenerate(int hpAmount, int manaAmount) {
        int healed = heal(hpAmount);
        int manaGained = gainMana(manaAmount);
        if (healed > 0 || manaGained > 0) {
            DomainEventPublisher.publish(new CharacterRegenerated(this, healed, manaGained));
        }
    }

    public SpellCasting.CastOutcome castSpell(Spell spell, AbstractCharacter target) {
        if (!trySpendMana(spell.manaCost())) {
            throw new IllegalStateException("Mana insuffisante pour lancer " + spell.name());
        }
        SpellCasting.CastOutcome outcome = getSpellCasting().cast(spell, target);
        DomainEventPublisher.publish(new SpellCast(this, spell, target, outcome.amount(), outcome.targetDefeated(),
                outcome.hit(), outcome.effectExpiresAt()));
        return outcome;
    }

    public int getXp() {
        return xp;
    }

    public void gainXp(int amount) {
        this.xp += amount;
        DomainEventPublisher.publish(new CharacterGainedXp(this, amount));
    }

    public int hitDieRecovery() {
        int hitDie = characterClass.hitDie();
        return Math.max(1, hitDie / 2 + 1 + getModifier(Attribute.CONSTITUTION));
    }

    public void applyLevelUp() {
        int hpGain = hitDieRecovery();
        level++;
        setMaxHealth(getMaxHealth() + hpGain);
        setCurrentHealth(getCurrentHealth() + hpGain);

        int manaGain = characterClass.manaGainPerLevel();
        maxMana += manaGain;
        currentMana += manaGain;

        DomainEventPublisher.publish(new CharacterLeveledUp(this, level, hpGain));
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

    public boolean takeDamage(int amount, AbstractCharacter attacker) {
        if (getCurrentHealth() <= 0) {
            return false;
        }
        setCurrentHealth(Math.max(0, getCurrentHealth() - amount));
        boolean defeated = getCurrentHealth() <= 0;
        DomainEventPublisher.publish(new GamePlayerDamaged(this, attacker, amount));
        if (defeated) {
            DomainEventPublisher.publish(new GamePlayerDied(this, attacker));
        }
        return defeated;
    }

    public void moveToZone(ZoneInstance destination) {
        moveToZone(destination, destination.getSpawnCell());
    }

    public void moveToZone(ZoneInstance destination, HexCoordinate targetCell) {
        ZoneInstance previous = getCurrentZone();
        previous.leave(this);
        destination.join(this, targetCell);
        DomainEventPublisher.publish(new GamePlayerMovedToZone(this, previous, destination));
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
        if (!(o instanceof CharacterInstance other)) {
            return false;
        }
        return level == other.level && xp == other.xp && inventory.getGold() == other.inventory.getGold()
                && getCurrentHealth() == other.getCurrentHealth() && getMaxHealth() == other.getMaxHealth()
                && shortRestCount == other.shortRestCount && Objects.equals(getId(), other.getId())
                && Objects.equals(getAccountId(), other.getAccountId()) && Objects.equals(getName(), other.getName())
                && Objects.equals(getCurrentZoneId(), other.getCurrentZoneId()) && gender == other.gender
                && race == other.race && characterClass == other.characterClass
                && Objects.equals(getAttributes(), other.getAttributes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getAccountId(), getName(), getCurrentZoneId(), gender, race, characterClass, level,
                xp, inventory.getGold(), getCurrentHealth(), getMaxHealth(), shortRestCount, getAttributes());
    }

    @Override
    public String toString() {
        return "GamePlayer[id=" + getId() + ", accountId=" + getAccountId() + ", name=" + getName() + ", currentZoneId="
                + getCurrentZoneId() + ", gender=" + gender + ", race=" + race + ", characterClass=" + characterClass
                + ", level=" + level + ", xp=" + xp + ", gold=" + inventory.getGold() + ", currentHealth="
                + getCurrentHealth() + ", maxHealth=" + getMaxHealth() + ", shortRestCount=" + shortRestCount
                + ", attributes=" + getAttributes() + "]";
    }
}
