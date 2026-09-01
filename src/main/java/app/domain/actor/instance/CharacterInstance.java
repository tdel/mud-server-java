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
import app.domain.actor.system.AppearanceSystem;
import app.domain.actor.system.ClassSystem;
import app.domain.actor.system.InventorySystem;
import app.domain.actor.event.CharacterGainedXp;
import app.domain.actor.event.CharacterLeveledUp;
import app.domain.actor.event.CharacterRegenerated;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.event.GamePlayerMovedToMap;
import app.domain.actor.event.GamePlayerRespawned;
import app.game.catalog.LevelCatalogHolder;
import app.domain.item.EquipmentSlot;
import app.domain.item.ItemSet;
import app.domain.map.Position;
import app.domain.item.Item;
import app.domain.world.MapInstance;
import app.domain.world.WorldInstance;
import app.game.catalog.ItemSetCatalogHolder;
import app.game.combat.CombatFormulas;
import app.network.Connection;
import app.network.OutputMessage;
import app.network.message.ingame.XpGained;

public final class CharacterInstance extends AbstractCharacter {

    private final Account account;
    private WorldInstance worldInstance;
    private final AppearanceSystem appearanceSystem;
    private final ClassSystem classSystem;
    private int level;

    private Connection connection;
    private final InventorySystem inventorySystem;
    private Party party;
    private PendingPartyInvite pendingInvite;
    private int xp;
    private int maxMana;
    private int currentMana;

    public CharacterInstance(UUID id, Account account, String name, MapInstance map, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold, int maxMana, int currentMana,
            Set<ActiveSkill> knownSkills, List<ActiveEffect> activeEffects, List<Subclass> subclasses,
            Set<PassiveSkill> knownPassiveSkills, List<Item> items) {
        super(id, name, attributes, currentHealth, maxHealth, knownSkills, knownPassiveSkills, activeEffects,
                computeBaseStats(attributes, level, items, race.speed()), false);
        this.account = account;
        getMotionSystem().setCurrentMap(map);
        this.appearanceSystem = new AppearanceSystem(this, gender, race);
        this.classSystem = new ClassSystem(this, characterClass, subclasses);
        this.level = level;
        this.xp = xp;
        this.inventorySystem = new InventorySystem(this, gold, items);
        this.maxMana = maxMana;
        this.currentMana = currentMana;
        inventorySystem.recomputeGradePenalty();
        getStatSystem().setSetBonuses(computeSetBonuses());
    }

    // Ne dépend que des paramètres reçus (aucun accès à `this`) : appelable
    // avant super(...) pour fournir la base initiale du StatSystem, et réutilisé
    // par recomputeStats() pour les recalculs post-construction (équipement,
    // level up).
    private static Map<ModifiedStat, Integer> computeBaseStats(Map<Attribute, Integer> attributes, int level,
            List<Item> items, int baseSpeed) {
        List<Item> equipped = items.stream().filter(item -> item.getSlot() != null).toList();
        Optional<Item> weapon = equipped.stream().filter(item -> item.getSlot() == EquipmentSlot.WEAPON).findFirst();

        int weaponPAtk = weapon.map(Item::getPAtk).orElse(CombatFormulas.UNARMED_PATK);
        int weaponMAtk = weapon.map(Item::getMAtk).orElse(0);
        int weaponAtkSpd = weapon.map(Item::getAtkSpd).orElse(CombatFormulas.BASE_ATK_SPD);
        int armorPDefSum = equipped.stream().mapToInt(Item::getPDef).sum();
        int armorMDefSum = equipped.stream().mapToInt(Item::getMDef).sum();
        int accuracyItemBonus = equipped.stream().mapToInt(Item::getAccuracyBonus).sum();
        int evasionItemBonus = equipped.stream().mapToInt(Item::getEvasionBonus).sum();
        int critItemBonus = equipped.stream().mapToInt(Item::getCritBonus).sum();
        int armorWeightPenalty = equipped.stream().filter(item -> item.getSlot() == EquipmentSlot.CHEST).findFirst()
                .map(item -> CombatFormulas.armorWeightPenalty(item.getArmorCategory())).orElse(0);

        Map<ModifiedStat, Integer> stats = CombatFormulas.baseStats(weaponPAtk, weaponMAtk, armorPDefSum, armorMDefSum,
                accuracyItemBonus, evasionItemBonus, critItemBonus, armorWeightPenalty, weaponAtkSpd, attributes,
                level);
        stats.put(ModifiedStat.SPEED, baseSpeed);
        return stats;
    }

    private Map<ModifiedStat, Integer> computeSetBonuses() {
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

    // Appelé après toute mutation de l'équipement (equip/unequip, voir
    // InventorySystem) ou de niveau (applyLevelUp) : p.atk/m.atk/accuracy/...
    // dépendent de l'arme/armure équipée et de level.
    public void recomputeStats() {
        int baseSpeed = getStatSystem().getBase(ModifiedStat.SPEED);
        getStatSystem()
                .updateBase(computeBaseStats(getAttributes(), getLevel(), inventorySystem.getItems(), baseSpeed));
        getStatSystem().setSetBonuses(computeSetBonuses());
    }

    public Account getAccount() {
        return account;
    }

    public UUID getAccountId() {
        return account.getId();
    }

    public WorldInstance getWorldInstance() {
        return worldInstance;
    }

    public void setWorldInstance(WorldInstance worldInstance) {
        this.worldInstance = worldInstance;
    }

    public AppearanceSystem getAppearanceSystem() {
        return appearanceSystem;
    }

    public ClassSystem getClassSystem() {
        return classSystem;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    @Override
    protected Map<SkillElement, Integer> elementalResistanceMap() {
        return inventorySystem.getEquippedItems().stream()
                .flatMap(item -> item.getElementalResistances().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum));
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
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
        getCombatSystem().clearTarget();
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

        int newMaxHealth = classSystem.getCharacterClass().maxHealth(getAttribute(Attribute.CONSTITUTION), level);
        int hpGain = newMaxHealth - getMaxHealth();
        setMaxHealth(newMaxHealth);
        setCurrentHealth(getCurrentHealth() + hpGain);

        int newMaxMana = classSystem.getCharacterClass().maxMana(getAttribute(Attribute.MEN), level);
        int manaGain = newMaxMana - maxMana;
        maxMana = newMaxMana;
        currentMana += manaGain;

        recomputeStats();

        DomainEventPublisher.publish(new CharacterLeveledUp(this, level, hpGain));
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
        MapInstance previous = getMotionSystem().getCurrentMap();
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
                + getMotionSystem().getCurrentMap().getTemplateId() + ", gender=" + appearanceSystem.getGender()
                + ", race=" + appearanceSystem.getRace() + ", characterClass=" + classSystem.getCharacterClass()
                + ", level=" + level + ", xp=" + xp + ", gold=" + inventorySystem.getGold() + ", currentHealth="
                + getCurrentHealth() + ", maxHealth=" + getMaxHealth() + ", attributes=" + getAttributes() + "]";
    }
}
