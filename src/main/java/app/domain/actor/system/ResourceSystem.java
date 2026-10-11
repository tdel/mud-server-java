package app.domain.actor.system;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.Attribute;
import app.domain.actor.ModifiedStat;
import app.domain.actor.event.CharacterRegenerated;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.instance.PlayerInstance;
import app.game.combat.CombatFormulas;
import app.network.message.ingame.RegenTick;

// Gère les réserves vitales d'un personnage (vie, mana ; pourra accueillir
// endurance/stamina etc. plus tard). La vie s'applique à tout AbstractCharacter
// (joueur, monstre, PNJ) ; le mana n'a de valeur réelle que pour un joueur
// (PlayerInstance reconfigure maxMana/currentMana après construction) — les
// autres gardent la valeur neutre par défaut (Integer.MAX_VALUE), jamais
// bloqués par le coût en mana d'un sort.
public final class ResourceSystem {

    private final AbstractCharacter character;
    private int currentHealth;
    private int maxHealth;
    private int currentMana = Integer.MAX_VALUE;
    private int maxMana = Integer.MAX_VALUE;

    public ResourceSystem(AbstractCharacter character, int currentHealth, int maxHealth) {
        this.character = character;
        this.currentHealth = currentHealth;
        this.maxHealth = maxHealth;
    }

    // Plafonné au max effectif : la fin d'un Battle Roar (MAX_HP) ramène les PV
    // sous le nouveau max, comme dans L2.
    public int getCurrentHealth() {
        int max = getMaxHealth();
        if (currentHealth > max) {
            currentHealth = max;
        }
        return currentHealth;
    }

    public void setCurrentHealth(int currentHealth) {
        this.currentHealth = currentHealth;
    }

    // Max de base (classe, level, CON) modifié par MAX_HP (Boost HP, Battle Roar).
    public int getMaxHealth() {
        StatSystem stats = character.getStatSystem();
        return stats == null ? maxHealth : Math.max(1, stats.adjust(ModifiedStat.MAX_HP, maxHealth));
    }

    public int getBaseMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(int maxHealth) {
        this.maxHealth = maxHealth;
    }

    public int heal(int amount) {
        int healed = Math.max(0, Math.min(amount, getMaxHealth() - getCurrentHealth()));
        currentHealth += healed;
        return healed;
    }

    public int getCurrentMana() {
        int max = getMaxMana();
        if (currentMana > max) {
            currentMana = max;
        }
        return currentMana;
    }

    public void setCurrentMana(int currentMana) {
        this.currentMana = currentMana;
    }

    // Max de base modifié par MAX_MP (Boost Mana, Soul of Sagittarius) — sauf
    // la valeur neutre des monstres/PNJ (Integer.MAX_VALUE).
    public int getMaxMana() {
        StatSystem stats = character.getStatSystem();
        if (stats == null || maxMana == Integer.MAX_VALUE) {
            return maxMana;
        }
        // Bonus de PM des robes/bijoux (base MAX_MP, cf.
        // InventorySystem.computeBaseStats)
        // ajouté avant les passifs et buffs.
        return Math.max(0, stats.adjust(ModifiedStat.MAX_MP, maxMana + stats.getBase(ModifiedStat.MAX_MP)));
    }

    public int getBaseMaxMana() {
        return maxMana;
    }

    public void setMaxMana(int maxMana) {
        this.maxMana = maxMana;
    }

    // PV sacrifiés au lancement d'un sort (Body To Mind) : jamais mortels.
    public boolean trySpendHealth(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (getCurrentHealth() <= amount) {
            return false;
        }
        currentHealth -= amount;
        return true;
    }

    public boolean trySpendMana(int amount) {
        if (getCurrentMana() < amount) {
            return false;
        }
        currentMana -= amount;
        return true;
    }

    public int gainMana(int amount) {
        if (maxMana == Integer.MAX_VALUE) {
            return 0;
        }
        int gained = Math.max(0, Math.min(amount, getMaxMana() - getCurrentMana()));
        currentMana += gained;
        return gained;
    }

    // Régénération L2 par tick de 3 s (Formulas.calcHpRegen/calcMpRegen) : table de
    // la classe au niveau x modificateur de niveau x CON / MEN x activité (assis
    // x1.5, immobile x1.1, en course x0.7), puis Relax/Armor Mastery...
    // (ModifiedStat.HP_REGEN/MP_REGEN). Seuls les joueurs régénèrent
    // (RegenHealthEngine/RegenManaEngine) : 0 pour les autres créatures.
    public int healthRegenAmountPerTick() {
        if (!(character instanceof PlayerInstance player)) {
            return 0;
        }
        int level = character.getLevelingSystem().getLevel();
        return character.getStatSystem().adjust(ModifiedStat.HP_REGEN,
                CombatFormulas.healthRegenPerTick(player.getClassSystem().vitals().at(level).hpRegen(), level,
                        character.getAttributeSystem().getAttribute(Attribute.CON), activityFactor()));
    }

    public int manaRegenAmountPerTick() {
        if (!(character instanceof PlayerInstance player)) {
            return 0;
        }
        int level = character.getLevelingSystem().getLevel();
        return character.getStatSystem().adjust(ModifiedStat.MP_REGEN,
                CombatFormulas.manaRegenPerTick(player.getClassSystem().vitals().at(level).mpRegen(), level,
                        character.getAttributeSystem().getAttribute(Attribute.MEN), activityFactor()));
    }

    private double activityFactor() {
        if (character.getEffectsSystem().isResting()) {
            return CombatFormulas.REGEN_RESTING_FACTOR;
        }
        return character.getMotionSystem().getActiveMovement() != null
                ? CombatFormulas.REGEN_MOVING_FACTOR
                : CombatFormulas.REGEN_STANDING_FACTOR;
    }

    // CharacterRegenerated ne porte qu'un PlayerInstance (seul un joueur est
    // suivi par RegenHealthEngine/RegenManaEngine) : no-op côté event sinon,
    // même convention que MotionSystem.respawn.
    public void regenerate(int hpAmount, int manaAmount) {
        int healed = heal(hpAmount);
        int manaGained = gainMana(manaAmount);
        if ((healed > 0 || manaGained > 0) && character instanceof PlayerInstance player) {
            player.send(new RegenTick(healed, manaGained, getCurrentHealth(), getMaxHealth(), getCurrentMana(),
                    getMaxMana()));
            DomainEventPublisher.publish(new CharacterRegenerated(player, healed, manaGained));
        }
    }
}
