package app.domain.actor.instance;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import app.domain.Account;
import app.domain.Party;
import app.domain.PendingPartyInvite;
import app.domain.Spell;
import app.domain.SpellElement;
import app.domain.actor.*;
import app.domain.actor.component.ActiveEffect;
import app.domain.actor.component.CharacterCombat;
import app.domain.actor.component.PlayerInventory;
import app.domain.actor.event.CharacterChoseSubclass;
import app.domain.actor.event.CharacterGainedXp;
import app.domain.actor.event.CharacterLeveledUp;
import app.domain.actor.event.CharacterLootedItem;
import app.domain.actor.event.CharacterReceivedGold;
import app.domain.actor.event.CharacterRegenerated;
import app.domain.actor.event.CharacterSpentGold;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.SubclassChoiceAvailable;
import app.domain.actor.event.GamePlayerDamaged;
import app.domain.actor.event.GamePlayerDied;
import app.domain.actor.event.GamePlayerEquippedItem;
import app.domain.actor.event.GamePlayerMovedToMap;
import app.domain.actor.event.GamePlayerRespawned;
import app.domain.actor.event.GamePlayerUnequippedItem;
import app.domain.actor.event.ItemDiscarded;
import app.domain.actor.event.ItemPurchased;
import app.domain.item.EquipmentSlot;
import app.domain.item.ItemSet;
import app.domain.map.Position;
import app.domain.item.Item;
import app.domain.world.MapInstance;
import app.domain.world.PeaceZone;
import app.domain.world.WorldInstance;
import app.game.catalog.ItemSetCatalogHolder;
import app.game.combat.CombatFormulas;
import app.game.dice.CheckResult;
import app.game.dice.DiceRoll;
import app.game.dice.DiceRoller;
import app.network.Connection;
import app.network.OutputMessage;

public final class CharacterInstance extends AbstractCharacter {

    private final Account account;
    private WorldInstance worldInstance;
    private Gender gender;
    private Race race;
    private CharacterClass characterClass;
    private int level;
    private Subclass subclassTier1;
    private Subclass subclassTier2;

    private Connection connection;
    private final PlayerInventory inventory;
    private final CharacterCombat combat;
    private Party party;
    private PendingPartyInvite pendingInvite;
    private int xp;
    private int shortRestCount;
    private int maxMana;
    private int currentMana;

    public static final int MAX_SHORT_RESTS_BEFORE_LONG_REST = 2;

    public CharacterInstance(UUID id, Account account, String name, MapInstance map, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold) {
        this(id, account, name, map, gender, race, characterClass, level, currentHealth, maxHealth, attributes, xp,
                gold, 0, 0, 0);
    }

    public CharacterInstance(UUID id, Account account, String name, MapInstance map, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold, int shortRestCount, int maxMana, int currentMana) {
        this(id, account, name, map, gender, race, characterClass, level, currentHealth, maxHealth, attributes, xp,
                gold, shortRestCount, maxMana, currentMana, Set.of(), List.of());
    }

    public CharacterInstance(UUID id, Account account, String name, MapInstance map, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold, int shortRestCount, int maxMana, int currentMana,
            Set<Spell> knownSpells, List<ActiveEffect> activeEffects) {
        this(id, account, name, map, gender, race, characterClass, level, currentHealth, maxHealth, attributes, xp,
                gold, shortRestCount, maxMana, currentMana, knownSpells, activeEffects, null, null);
    }

    public CharacterInstance(UUID id, Account account, String name, MapInstance map, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold, int shortRestCount, int maxMana, int currentMana,
            Set<Spell> knownSpells, List<ActiveEffect> activeEffects, Subclass subclassTier1, Subclass subclassTier2) {
        super(id, name, attributes, currentHealth, maxHealth);
        this.account = account;
        setCurrentMap(map);
        this.gender = gender;
        this.race = race;
        this.speed = race.speed();
        this.characterClass = characterClass;
        this.level = level;
        this.subclassTier1 = subclassTier1;
        this.subclassTier2 = subclassTier2;
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

    public UUID getCurrentMapId() {
        return getCurrentMap().getTemplateId();
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

    public Subclass getSubclassTier1() {
        return subclassTier1;
    }

    public Subclass getSubclassTier2() {
        return subclassTier2;
    }

    // tier 1 (niveau 20) puis tier 2 (niveau 40) restent en attente tant qu'aucune
    // option n'a été
    // choisie ; dérivé de l'état plutôt que stocké, pour survivre à une reconnexion
    // sans champ dédié.
    public Integer getPendingSubclassTier() {
        if (subclassTier1 == null && level >= 20) {
            return 1;
        }
        if (subclassTier2 == null && level >= 40) {
            return 2;
        }
        return null;
    }

    public void chooseSubclass(Subclass subclass) {
        Integer tier = getPendingSubclassTier();
        if (tier == null || !Subclass.availableAt(characterClass, tier).contains(subclass)) {
            throw new IllegalStateException("Choix de sous-classe invalide: " + subclass + " (tier=" + tier
                    + ", classe=" + characterClass + ")");
        }
        if (tier == 1) {
            subclassTier1 = subclass;
        } else {
            subclassTier2 = subclass;
        }
        DomainEventPublisher.publish(new CharacterChoseSubclass(this, tier, subclass));
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
    protected int basePAtk() {
        return getEquippedWeapon().map(Item::getPAtk).orElse(CombatFormulas.UNARMED_PATK);
    }

    @Override
    protected int baseMAtk() {
        return getEquippedWeapon().map(Item::getMAtk).orElse(0);
    }

    @Override
    protected int basePDefSum() {
        return inventory.getEquippedItems().stream().mapToInt(Item::getPDef).sum();
    }

    @Override
    protected int baseMDefSum() {
        return inventory.getEquippedItems().stream().mapToInt(Item::getMDef).sum();
    }

    @Override
    protected int accuracyItemBonus() {
        return inventory.getEquippedItems().stream().mapToInt(Item::getAccuracyBonus).sum();
    }

    @Override
    protected int evasionItemBonus() {
        return inventory.getEquippedItems().stream().mapToInt(Item::getEvasionBonus).sum();
    }

    @Override
    protected int critItemBonus() {
        return inventory.getEquippedItems().stream().mapToInt(Item::getCritBonus).sum();
    }

    @Override
    protected int armorWeightPenalty() {
        return inventory.getEquippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.CHEST).findFirst()
                .map(item -> CombatFormulas.armorWeightPenalty(item.getArmorCategory())).orElse(0);
    }

    @Override
    protected Map<SpellElement, Integer> elementalResistanceMap() {
        return inventory.getEquippedItems().stream().flatMap(item -> item.getElementalResistances().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum));
    }

    @Override
    protected Map<ModifiedStat, Integer> setBonusModifiers() {
        Map<String, Long> equippedCountBySetId = inventory.getEquippedItems().stream()
                .map(item -> item.getTemplate().getSetId()).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(setId -> setId, Collectors.counting()));

        Map<ModifiedStat, Integer> modifiers = new EnumMap<>(ModifiedStat.class);
        for (Map.Entry<String, Long> entry : equippedCountBySetId.entrySet()) {
            ItemSet set = ItemSetCatalogHolder.getById(entry.getKey());
            int piecesEquipped = entry.getValue().intValue();
            for (Map.Entry<Integer, Map<ModifiedStat, Integer>> tier : set.bonusByPieceCount().entrySet()) {
                if (piecesEquipped >= tier.getKey()) {
                    tier.getValue().forEach((stat, amount) -> modifiers.merge(stat, amount, Integer::sum));
                }
            }
        }
        return modifiers;
    }

    private Optional<Item> getEquippedWeapon() {
        return inventory.getEquippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.WEAPON).findFirst();
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

    public Party getParty() {
        return party;
    }

    public void setParty(Party party) {
        this.party = party;
    }

    public PendingPartyInvite getPendingInvite() {
        return pendingInvite;
    }

    public void setPendingInvite(PendingPartyInvite pendingInvite) {
        this.pendingInvite = pendingInvite;
    }

    @Override
    public Set<Spell> getGrantedSpells() {
        return inventory.getEquippedItems().stream().flatMap(item -> item.getTemplate().getGrantedSpells().stream())
                .collect(Collectors.toSet());
    }

    @Override
    public int getMaxMana() {
        return maxMana;
    }

    public void setMaxMana(int maxMana) {
        this.maxMana = maxMana;
    }

    @Override
    public int getCurrentMana() {
        return currentMana;
    }

    public void setCurrentMana(int currentMana) {
        this.currentMana = currentMana;
    }

    @Override
    public boolean trySpendMana(int amount) {
        if (currentMana < amount) {
            return false;
        }
        currentMana -= amount;
        return true;
    }

    @Override
    public void clearCombatTarget() {
        combat.setTarget(null);
    }

    public int gainMana(int amount) {
        int gained = Math.min(amount, maxMana - currentMana);
        currentMana += gained;
        return gained;
    }

    public int healthRegenAmountPerTick() {
        return CombatFormulas.healthRegenPerTick(getMaxHealth(), getAttribute(Attribute.CONSTITUTION));
    }

    public int manaRegenAmountPerTick() {
        return CombatFormulas.manaRegenPerTick(getMaxMana(), getAttribute(Attribute.MEN));
    }

    public void regenerate(int hpAmount, int manaAmount) {
        int healed = heal(hpAmount);
        int manaGained = gainMana(manaAmount);
        if (healed > 0 || manaGained > 0) {
            DomainEventPublisher.publish(new CharacterRegenerated(this, healed, manaGained));
        }
    }

    public int getXp() {
        return xp;
    }

    public void gainXp(int amount) {
        this.xp += amount;
        DomainEventPublisher.publish(new CharacterGainedXp(this, amount));
    }

    public void applyLevelUp() {
        level++;

        int newMaxHealth = CombatFormulas.maxHealth(characterClass.hitDie(), getAttribute(Attribute.CONSTITUTION),
                level);
        int hpGain = newMaxHealth - getMaxHealth();
        setMaxHealth(newMaxHealth);
        setCurrentHealth(getCurrentHealth() + hpGain);

        int newMaxMana = CombatFormulas.maxMana(characterClass.manaGainPerLevel(), getAttribute(Attribute.MEN), level);
        int manaGain = newMaxMana - maxMana;
        maxMana = newMaxMana;
        currentMana += manaGain;

        DomainEventPublisher.publish(new CharacterLeveledUp(this, level, hpGain));

        Integer pendingTier = getPendingSubclassTier();
        if (pendingTier != null) {
            List<Subclass> options = Subclass.availableAt(characterClass, pendingTier);
            if (!options.isEmpty()) {
                DomainEventPublisher.publish(new SubclassChoiceAvailable(this, pendingTier, options));
            }
        }
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
        if (getZone() instanceof PeaceZone) {
            return false;
        }
        setCurrentHealth(Math.max(0, getCurrentHealth() - amount));
        boolean defeated = getCurrentHealth() <= 0;
        DomainEventPublisher.publish(new GamePlayerDamaged(this, attacker, amount));
        if (defeated) {
            getCombat().setTarget(null);
            DomainEventPublisher.publish(new GamePlayerDied(this, attacker));
        }
        return defeated;
    }

    public void respawn(MapInstance destination, Position position) {
        setCurrentHealth(Math.max(1, getMaxHealth() / 4));
        setCurrentMana(0);
        moveToMap(destination, position);
        DomainEventPublisher.publish(new GamePlayerRespawned(this));
    }

    public void moveToMap(MapInstance destination) {
        moveToMap(destination, destination.getSpawnPosition());
    }

    public void moveToMap(MapInstance destination, Position targetPosition) {
        MapInstance previous = getCurrentMap();
        previous.leave(this);
        destination.join(this, targetPosition);
        DomainEventPublisher.publish(new GamePlayerMovedToMap(this, previous, destination));
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
                && Objects.equals(getCurrentMapId(), other.getCurrentMapId()) && gender == other.gender
                && race == other.race && characterClass == other.characterClass
                && Objects.equals(getAttributes(), other.getAttributes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getAccountId(), getName(), getCurrentMapId(), gender, race, characterClass, level,
                xp, inventory.getGold(), getCurrentHealth(), getMaxHealth(), shortRestCount, getAttributes());
    }

    @Override
    public String toString() {
        return "GamePlayer[id=" + getId() + ", accountId=" + getAccountId() + ", name=" + getName() + ", currentMapId="
                + getCurrentMapId() + ", gender=" + gender + ", race=" + race + ", characterClass=" + characterClass
                + ", level=" + level + ", xp=" + xp + ", gold=" + inventory.getGold() + ", currentHealth="
                + getCurrentHealth() + ", maxHealth=" + getMaxHealth() + ", shortRestCount=" + shortRestCount
                + ", attributes=" + getAttributes() + "]";
    }
}
