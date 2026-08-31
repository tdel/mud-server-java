package app.domain.actor.instance;

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
import app.domain.PassiveSkill;
import app.domain.PendingPartyInvite;
import app.domain.ActiveSkill;
import app.domain.SkillElement;
import app.domain.actor.*;
import app.domain.ActiveEffect;
import app.domain.actor.system.CombatSystem;
import app.domain.actor.system.InventorySystem;
import app.domain.actor.event.CharacterChoseSubclass;
import app.domain.actor.event.CharacterGainedXp;
import app.domain.actor.event.CharacterLeveledUp;
import app.domain.actor.event.CharacterRegenerated;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.GamePlayerDamaged;
import app.domain.actor.event.GamePlayerDied;
import app.domain.actor.event.GamePlayerMovedToMap;
import app.domain.actor.event.GamePlayerRespawned;
import app.game.catalog.LevelCatalogHolder;
import app.domain.item.EquipmentSlot;
import app.domain.item.ItemSet;
import app.domain.map.Position;
import app.domain.item.Item;
import app.domain.world.MapInstance;
import app.domain.world.PeaceZone;
import app.domain.world.WorldInstance;
import app.game.catalog.ItemSetCatalogHolder;
import app.game.combat.CombatFormulas;
import app.network.Connection;
import app.network.OutputMessage;
import app.network.message.ingame.XpGained;

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
    private final InventorySystem inventorySystem;
    private final CombatSystem combatSystem;
    private Party party;
    private PendingPartyInvite pendingInvite;
    private int xp;
    private int maxMana;
    private int currentMana;

    public CharacterInstance(UUID id, Account account, String name, MapInstance map, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold) {
        this(id, account, name, map, gender, race, characterClass, level, currentHealth, maxHealth, attributes, xp,
                gold, 0, 0);
    }

    public CharacterInstance(UUID id, Account account, String name, MapInstance map, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold, int maxMana, int currentMana) {
        this(id, account, name, map, gender, race, characterClass, level, currentHealth, maxHealth, attributes, xp,
                gold, maxMana, currentMana, Set.of(), List.of());
    }

    public CharacterInstance(UUID id, Account account, String name, MapInstance map, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold, int maxMana, int currentMana,
            Set<ActiveSkill> knownSkills, List<ActiveEffect> activeEffects) {
        this(id, account, name, map, gender, race, characterClass, level, currentHealth, maxHealth, attributes, xp,
                gold, maxMana, currentMana, knownSkills, activeEffects, null, null, Set.of());
    }

    public CharacterInstance(UUID id, Account account, String name, MapInstance map, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold, int maxMana, int currentMana,
            Set<ActiveSkill> knownSkills, List<ActiveEffect> activeEffects, Subclass subclassTier1,
            Subclass subclassTier2, Set<PassiveSkill> knownPassiveSkills) {
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
        this.inventorySystem = new InventorySystem(this, gold);
        this.combatSystem = new CombatSystem(this);
        this.maxMana = maxMana;
        this.currentMana = currentMana;
        knownSkills.forEach(getSkillSystem()::learn);
        activeEffects.forEach(getEffectsSystem()::apply);
        knownPassiveSkills.forEach(getSkillSystem()::learn);
        inventorySystem.recomputeGradePenalty();
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

    public Race getRace() {
        return race;
    }

    public CharacterClass getCharacterClass() {
        return characterClass;
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

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
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
        return inventorySystem.getEquippedItems().stream().mapToInt(Item::getPDef).sum();
    }

    @Override
    protected int baseMDefSum() {
        return inventorySystem.getEquippedItems().stream().mapToInt(Item::getMDef).sum();
    }

    @Override
    protected int accuracyItemBonus() {
        return inventorySystem.getEquippedItems().stream().mapToInt(Item::getAccuracyBonus).sum();
    }

    @Override
    protected int evasionItemBonus() {
        return inventorySystem.getEquippedItems().stream().mapToInt(Item::getEvasionBonus).sum();
    }

    @Override
    protected int critItemBonus() {
        return inventorySystem.getEquippedItems().stream().mapToInt(Item::getCritBonus).sum();
    }

    @Override
    protected int baseAtkSpd() {
        return getEquippedWeapon().map(Item::getAtkSpd).orElse(CombatFormulas.BASE_ATK_SPD);
    }

    @Override
    protected int armorWeightPenalty() {
        return inventorySystem.getEquippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.CHEST)
                .findFirst().map(item -> CombatFormulas.armorWeightPenalty(item.getArmorCategory())).orElse(0);
    }

    @Override
    protected Map<SkillElement, Integer> elementalResistanceMap() {
        return inventorySystem.getEquippedItems().stream()
                .flatMap(item -> item.getElementalResistances().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum));
    }

    @Override
    protected Map<ModifiedStat, Integer> setBonusModifiers() {
        Map<String, Long> equippedCountBySetId = inventorySystem.getEquippedItems().stream().map(Item::getSetId)
                .filter(Objects::nonNull).collect(Collectors.groupingBy(setId -> setId, Collectors.counting()));

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
        return inventorySystem.getEquippedItems().stream().filter(item -> item.getSlot() == EquipmentSlot.WEAPON)
                .findFirst();
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public CombatSystem getCombatSystem() {
        return combatSystem;
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
        combatSystem.clearTarget();
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
        send(new XpGained(amount));

        while (level < LevelCatalogHolder.maxLevel() && xp >= LevelCatalogHolder.xpRequiredForLevel(level + 1)) {
            applyLevelUp();
        }

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
            getCombatSystem().setTarget(null);
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

    public InventorySystem getInventorySystem() {
        return inventorySystem;
    }

    @Override
    public void send(OutputMessage message) {
        if (null != connection) {
            this.connection.send(message);
        }
    }

    @Override
    public String toString() {
        return "GamePlayer[id=" + getId() + ", accountId=" + getAccountId() + ", name=" + getName() + ", currentMapId="
                + getCurrentMapId() + ", gender=" + gender + ", race=" + race + ", characterClass=" + characterClass
                + ", level=" + level + ", xp=" + xp + ", gold=" + inventorySystem.getGold() + ", currentHealth="
                + getCurrentHealth() + ", maxHealth=" + getMaxHealth() + ", attributes=" + getAttributes() + "]";
    }
}
