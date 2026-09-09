package app.domain.actor.instance;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import app.domain.actor.system.LevelingSystem;
import app.domain.actor.system.PvPSystem;
import app.domain.item.EquipmentSlot;
import app.domain.item.ItemGrade;
import app.domain.item.ItemSet;
import app.domain.item.Item;
import app.domain.world.MapInstance;
import app.domain.world.WorldInstance;
import app.game.catalog.ItemSetCatalogHolder;
import app.game.combat.CombatFormulas;
import app.network.Connection;
import app.network.OutputMessage;

public final class PlayerInstance extends AbstractCharacter {

    private final Account account;
    private WorldInstance worldInstance;
    private final AppearanceSystem appearanceSystem;
    private final ClassSystem classSystem;
    private final LevelingSystem levelingSystem;

    private Connection connection;
    private final InventorySystem inventorySystem;
    private Party party;
    private PendingPartyInvite pendingInvite;
    private volatile ItemGrade activeSoulshotGrade;
    private volatile ItemGrade activeSpiritshotGrade;
    private final PvPSystem pvpSystem;

    public PlayerInstance(UUID id, Account account, String name, MapInstance map, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold, int maxMana, int currentMana,
            Map<ActiveSkill, Integer> knownSkills, List<ActiveEffect> activeEffects, List<Subclass> subclasses,
            Map<PassiveSkill, Integer> knownPassiveSkills, List<Item> items, ItemGrade activeSoulshotGrade,
            ItemGrade activeSpiritshotGrade, int karma, int pkCount, int pvpCount, boolean pvpFlagged) {
        super(id, name, attributes, currentHealth, maxHealth, knownSkills, knownPassiveSkills, activeEffects,
                computeBaseStats(attributes, level, items, race.speed()), false, 0, 0, List.of());
        this.account = account;
        getMotionSystem().setCurrentMap(map);
        this.appearanceSystem = new AppearanceSystem(this, gender, race);
        this.classSystem = new ClassSystem(this, characterClass, subclasses);
        this.levelingSystem = new LevelingSystem(this, level, xp);
        this.inventorySystem = new InventorySystem(this, gold, items);
        getResourceSystem().setMaxMana(maxMana);
        getResourceSystem().setCurrentMana(currentMana);
        this.activeSoulshotGrade = activeSoulshotGrade;
        this.activeSpiritshotGrade = activeSpiritshotGrade;
        this.pvpSystem = new PvPSystem(this, karma, pkCount, pvpCount, pvpFlagged);
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

    @Override
    public int getLevel() {
        return levelingSystem.getLevel();
    }

    public LevelingSystem getLevelingSystem() {
        return levelingSystem;
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
    public void clearCombatTarget() {
        getCombatSystem().clearTarget();
    }

    public InventorySystem getInventorySystem() {
        return inventorySystem;
    }

    public PvPSystem getPvpSystem() {
        return pvpSystem;
    }

    public ItemGrade getActiveSoulshotGrade() {
        return activeSoulshotGrade;
    }

    public void setActiveSoulshotGrade(ItemGrade activeSoulshotGrade) {
        this.activeSoulshotGrade = activeSoulshotGrade;
    }

    public ItemGrade getActiveSpiritshotGrade() {
        return activeSpiritshotGrade;
    }

    public void setActiveSpiritshotGrade(ItemGrade activeSpiritshotGrade) {
        this.activeSpiritshotGrade = activeSpiritshotGrade;
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
                + ", level=" + levelingSystem.getLevel() + ", xp=" + levelingSystem.getXp() + ", gold="
                + inventorySystem.getGold() + ", currentHealth=" + getResourceSystem().getCurrentHealth()
                + ", maxHealth=" + getResourceSystem().getMaxHealth() + ", attributes=" + getAttributes() + "]";
    }
}
