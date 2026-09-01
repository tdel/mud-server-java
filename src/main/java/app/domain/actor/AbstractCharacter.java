package app.domain.actor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import app.domain.ActiveEffect;
import app.domain.ActiveSkill;
import app.domain.PassiveSkill;
import app.domain.SkillElement;
import app.domain.actor.system.EffectsSystem;
import app.domain.actor.system.MotionSystem;
import app.domain.actor.system.SkillSystem;
import app.domain.actor.system.StatSystem;
import app.domain.actor.instance.CharacterInstance;
import app.domain.world.MapInstance;
import app.network.OutputMessage;

public abstract class AbstractCharacter extends AbstractObject {

    private final Map<Attribute, Integer> attributes;
    private final EffectsSystem effectsSystem = new EffectsSystem(this);
    private final SkillSystem skillSystem = new SkillSystem(this);
    private final MotionSystem motionSystem = new MotionSystem(this);
    private final StatSystem statSystem;
    private int currentHealth;
    private int maxHealth;

    private final KnownList knownList = new KnownList(this);

    protected AbstractCharacter(UUID id, String name, Map<Attribute, Integer> attributes, int currentHealth,
            int maxHealth, Set<ActiveSkill> knownSkills, Set<PassiveSkill> knownPassiveSkills,
            List<ActiveEffect> activeEffects, Map<ModifiedStat, Integer> initialBaseStats) {
        super(id, name);
        this.attributes = new EnumMap<>(attributes);
        this.currentHealth = currentHealth;
        this.maxHealth = maxHealth;
        this.statSystem = new StatSystem(effectsSystem, initialBaseStats);
        knownSkills.forEach(getSkillSystem()::learn);
        knownPassiveSkills.forEach(getSkillSystem()::learn);
        activeEffects.forEach(getEffectsSystem()::apply);
    }

    public int getAttribute(Attribute attribute) {
        return attributes.get(attribute);
    }

    public int getModifier(Attribute attribute) {
        return Math.floorDiv(getAttribute(attribute) - 10, 2);
    }

    public abstract int getLevel();

    // Défaut neutre : seul CharacterInstance a des objets équipés susceptibles
    // de porter des résistances élémentaires ; MonsterInstance la surcharge.
    protected Map<SkillElement, Integer> elementalResistanceMap() {
        return Map.of();
    }

    public final int getElementalResistance(SkillElement element) {
        return elementalResistanceMap().getOrDefault(element, 0);
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

    // Défaut neutre : seul CharacterInstance suit une réserve de mana ;
    // MonsterInstance/AbstractNpc n'en ont pas encore, donc jamais bloqués par le
    // coût en mana d'un sort.
    public int getCurrentMana() {
        return Integer.MAX_VALUE;
    }

    public int getMaxMana() {
        return Integer.MAX_VALUE;
    }

    public boolean trySpendMana(int amount) {
        return true;
    }

    // Défaut neutre : seul CharacterInstance a une CharacterCombat dont la cible
    // doit être effacée après un kill ; le ciblage d'un MonsterInstance (pursuit,
    // MonsterAiEngine) se recalcule de lui-même au prochain tick d'IA.
    public void clearCombatTarget() {
    }

    public Map<Attribute, Integer> getAttributes() {
        return Map.copyOf(attributes);
    }

    public int getCurrentHealth() {
        return currentHealth;
    }

    public void setCurrentHealth(int currentHealth) {
        this.currentHealth = currentHealth;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(int maxHealth) {
        this.maxHealth = maxHealth;
    }

    public int heal(int amount) {
        int healed = Math.min(amount, maxHealth - currentHealth);
        currentHealth += healed;
        return healed;
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
    public void broadcast(OutputMessage message, CharacterInstance exclude) {
        for (AbstractCharacter known : knownList.asList()) {
            if (known instanceof CharacterInstance target && target != exclude) {
                target.send(message);
            }
        }
        if (this instanceof CharacterInstance self && self != exclude) {
            self.send(message);
        }
    }

    /**
     * Diffuse un message à TOUS les joueurs de la carte courante, sans passer par
     * la KnownList (portée de perception) — réservé aux événements de
     * présence/absence d'une entité (arrivée/départ d'un joueur, apparition/mort
     * d'un monstre) : la sélection d'une cible ne doit pas dépendre de la distance,
     * contrairement aux diffusions de mouvement/combat/chat qui, elles, restent
     * scopées à {@link #broadcast} pour la bande passante.
     */
    public void broadcastToMap(OutputMessage message, CharacterInstance exclude) {
        MapInstance currentMap = getMotionSystem().getCurrentMap();
        if (currentMap == null) {
            return;
        }
        for (CharacterInstance target : currentMap.characters()) {
            if (target != exclude) {
                target.send(message);
            }
        }
    }

}
