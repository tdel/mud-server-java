package app.domain.actor.system;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.Attribute;
import app.domain.actor.event.CharacterRegenerated;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.instance.PlayerInstance;
import app.game.combat.CombatFormulas;

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

    public int getCurrentMana() {
        return currentMana;
    }

    public void setCurrentMana(int currentMana) {
        this.currentMana = currentMana;
    }

    public int getMaxMana() {
        return maxMana;
    }

    public void setMaxMana(int maxMana) {
        this.maxMana = maxMana;
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
        return CombatFormulas.healthRegenPerTick(maxHealth, character.getAttribute(Attribute.CON));
    }

    public int manaRegenAmountPerTick() {
        return CombatFormulas.manaRegenPerTick(maxMana, character.getAttribute(Attribute.MEN));
    }

    // CharacterRegenerated ne porte qu'un PlayerInstance (seul un joueur est
    // suivi par RegenHealthEngine/RegenManaEngine) : no-op côté event sinon,
    // même convention que MotionSystem.respawn.
    public void regenerate(int hpAmount, int manaAmount) {
        int healed = heal(hpAmount);
        int manaGained = gainMana(manaAmount);
        if ((healed > 0 || manaGained > 0) && character instanceof PlayerInstance player) {
            DomainEventPublisher.publish(new CharacterRegenerated(player, healed, manaGained));
        }
    }
}
