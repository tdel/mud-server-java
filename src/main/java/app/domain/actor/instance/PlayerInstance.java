package app.domain.actor.instance;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import app.domain.Account;
import app.domain.PassiveSkill;
import app.domain.ActiveSkill;
import app.domain.actor.*;
import app.domain.ActiveEffect;
import app.domain.actor.system.AppearanceSystem;
import app.domain.actor.system.ClassSystem;
import app.domain.actor.system.InventorySystem;
import app.domain.actor.system.PartySystem;
import app.domain.actor.system.PvPSystem;
import app.domain.item.ItemGrade;
import app.domain.item.Item;
import app.domain.world.MapInstance;
import app.domain.world.WorldInstance;
import app.network.Connection;
import app.network.OutputMessage;

public final class PlayerInstance extends AbstractCharacter {

    private final Account account;
    private WorldInstance worldInstance;
    private final AppearanceSystem appearanceSystem;
    private final ClassSystem classSystem;

    private Connection connection;
    private final PartySystem partySystem = new PartySystem();
    private final PvPSystem pvpSystem;

    public PlayerInstance(UUID id, Account account, String name, MapInstance map, Gender gender, Race race,
            CharacterClass characterClass, int level, int currentHealth, int maxHealth,
            Map<Attribute, Integer> attributes, int xp, int gold, int maxMana, int currentMana,
            Map<ActiveSkill, Integer> knownSkills, List<ActiveEffect> activeEffects, List<Subclass> subclasses,
            Map<PassiveSkill, Integer> knownPassiveSkills, List<Item> items, ItemGrade activeSoulshotGrade,
            ItemGrade activeSpiritshotGrade, int karma, int pkCount, int pvpCount, boolean pvpFlagged) {
        super(id, name, attributes, currentHealth, maxHealth, knownSkills, knownPassiveSkills, activeEffects,
                InventorySystem.computeBaseStats(attributes, level, items, race.speed()), false, 0, 0, List.of(), level,
                xp, gold, items, activeSoulshotGrade, activeSpiritshotGrade);
        this.account = account;
        getMotionSystem().setCurrentMap(map);
        this.appearanceSystem = new AppearanceSystem(this, gender, race);
        this.classSystem = new ClassSystem(this, characterClass, subclasses);
        getResourceSystem().setMaxMana(maxMana);
        getResourceSystem().setCurrentMana(currentMana);
        this.pvpSystem = new PvPSystem(this, karma, pkCount, pvpCount, pvpFlagged);
        getInventorySystem().recomputeGradePenalty();
        getStatSystem().setSetBonuses(getInventorySystem().computeSetBonuses());
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

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public PartySystem getPartySystem() {
        return partySystem;
    }

    @Override
    public void clearCombatTarget() {
        getCombatSystem().clearTarget();
    }

    public PvPSystem getPvpSystem() {
        return pvpSystem;
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
                + ", level=" + getLevelingSystem().getLevel() + ", xp=" + getLevelingSystem().getXp() + ", gold="
                + getInventorySystem().getGold() + ", currentHealth=" + getResourceSystem().getCurrentHealth()
                + ", maxHealth=" + getResourceSystem().getMaxHealth() + ", attributes="
                + getAttributeSystem().getAttributes() + "]";
    }
}
