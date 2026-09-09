package app.domain.actor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.domain.ActiveEffect;
import app.domain.ActiveSkill;
import app.domain.PassiveSkill;
import app.domain.actor.system.AttributeSystem;
import app.domain.actor.system.CombatSystem;
import app.domain.actor.system.EffectsSystem;
import app.domain.actor.system.InventorySystem;
import app.domain.actor.system.LevelingSystem;
import app.domain.actor.system.LootSystem;
import app.domain.actor.system.MotionSystem;
import app.domain.actor.system.ResourceSystem;
import app.domain.actor.system.SkillSystem;
import app.domain.actor.system.StatSystem;
import app.domain.actor.instance.PlayerInstance;
import app.domain.item.Item;
import app.domain.item.ItemGrade;
import app.domain.item.LootTableEntry;
import app.domain.world.MapInstance;
import app.network.OutputMessage;

public abstract class AbstractCharacter extends AbstractObject {

    private static final Logger log = LoggerFactory.getLogger(AbstractCharacter.class);

    private final AttributeSystem attributeSystem;
    private final EffectsSystem effectsSystem = new EffectsSystem(this);
    private final SkillSystem skillSystem = new SkillSystem(this);
    private final MotionSystem motionSystem = new MotionSystem(this);
    private final CombatSystem combatSystem;
    private final StatSystem statSystem;
    private final LootSystem lootSystem;
    private final ResourceSystem resourceSystem;
    private final LevelingSystem levelingSystem;
    private final InventorySystem inventorySystem;

    private final KnownList knownList = new KnownList(this);

    protected AbstractCharacter(UUID id, String name, Map<Attribute, Integer> attributes, int currentHealth,
            int maxHealth, Map<ActiveSkill, Integer> knownSkills, Map<PassiveSkill, Integer> knownPassiveSkills,
            List<ActiveEffect> activeEffects, Map<ModifiedStat, Integer> initialBaseStats, boolean invulnerable,
            int xpReward, int goldReward, List<LootTableEntry> lootTable, int level, int xp, int gold, List<Item> items,
            ItemGrade activeSoulshotGrade, ItemGrade activeSpiritshotGrade) {
        super(id, name);
        this.attributeSystem = new AttributeSystem(attributes);
        this.resourceSystem = new ResourceSystem(this, currentHealth, maxHealth);
        this.statSystem = new StatSystem(effectsSystem, initialBaseStats);
        this.combatSystem = new CombatSystem(this, invulnerable);
        this.lootSystem = new LootSystem(this, xpReward, goldReward, lootTable);
        this.levelingSystem = new LevelingSystem(this, level, xp);
        this.inventorySystem = new InventorySystem(this, gold, items, activeSoulshotGrade, activeSpiritshotGrade);
        knownSkills.forEach((skill, level2) -> getSkillSystem().learn(skill, level2));
        knownPassiveSkills.forEach((passiveSkill, level2) -> getSkillSystem().learn(passiveSkill, level2));
        activeEffects.forEach(getEffectsSystem()::apply);
    }

    public AttributeSystem getAttributeSystem() {
        return attributeSystem;
    }

    public LevelingSystem getLevelingSystem() {
        return levelingSystem;
    }

    public InventorySystem getInventorySystem() {
        return inventorySystem;
    }

    public boolean takeDamage(int amount, AbstractCharacter attacker) {
        return combatSystem.takeDamage(amount, attacker);
    }

    public EffectsSystem getEffectsSystem() {
        return effectsSystem;
    }

    public StatSystem getStatSystem() {
        return statSystem;
    }

    public SkillSystem getSkillSystem() {
        return skillSystem;
    }

    public MotionSystem getMotionSystem() {
        return motionSystem;
    }

    public CombatSystem getCombatSystem() {
        return combatSystem;
    }

    public LootSystem getLootSystem() {
        return lootSystem;
    }

    public ResourceSystem getResourceSystem() {
        return resourceSystem;
    }

    // Défaut neutre : seul PlayerInstance a une CharacterCombat dont la cible
    // doit être effacée après un kill ; le ciblage d'un MonsterInstance (pursuit,
    // MonsterAiEngine) se recalcule de lui-même au prochain tick d'IA.
    public void clearCombatTarget() {
    }

    // No-op par défaut : seul GamePlayer a une Connection à notifier.
    public void send(OutputMessage message) {
    }

    public KnownList getKnownList() {
        return knownList;
    }

    /**
     * Diffuse un message à tous les personnages qui connaissent actuellement ce
     * personnage (sa KnownList), plus lui-même s'il s'agit d'un joueur (l'auteur
     * d'une action reçoit toujours sa propre diffusion, même s'il n'apparaît pas
     * dans sa propre KnownList).
     */
    public void broadcast(OutputMessage message, PlayerInstance exclude) {
        int recipients = 0;
        for (AbstractCharacter known : knownList.asList()) {
            if (known instanceof PlayerInstance target && target != exclude) {
                target.send(message);
                recipients++;
            }
        }
        if (this instanceof PlayerInstance self && self != exclude) {
            self.send(message);
            recipients++;
        }
        log.info("character.broadcast type={} recipients={}", message.getClass().getSimpleName(), recipients);
    }

    /**
     * Diffuse un message à TOUS les joueurs de la carte courante, sans passer par
     * la KnownList (portée de perception) — réservé aux événements de
     * présence/absence d'une entité (arrivée/départ d'un joueur, apparition/mort
     * d'un monstre) : la sélection d'une cible ne doit pas dépendre de la distance,
     * contrairement aux diffusions de mouvement/combat/chat qui, elles, restent
     * scopées à {@link #broadcast} pour la bande passante.
     */
    public void broadcastToMap(OutputMessage message, PlayerInstance exclude) {
        MapInstance currentMap = getMotionSystem().getCurrentMap();
        if (currentMap == null) {
            return;
        }
        for (PlayerInstance target : currentMap.characters()) {
            if (target != exclude) {
                target.send(message);
            }
        }
    }

}
