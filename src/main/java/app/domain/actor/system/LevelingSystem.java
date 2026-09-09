package app.domain.actor.system;

import app.domain.actor.Attribute;
import app.domain.actor.event.CharacterGainedXp;
import app.domain.actor.event.CharacterLeveledUp;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.instance.PlayerInstance;
import app.game.catalog.LevelCatalogHolder;
import app.network.message.ingame.PlayerLeveledUp;
import app.network.message.ingame.XpGained;

public final class LevelingSystem {

    private final PlayerInstance character;
    private int level;
    private int xp;

    public LevelingSystem(PlayerInstance character, int level, int xp) {
        this.character = character;
        this.level = level;
        this.xp = xp;
    }

    public int getLevel() {
        return level;
    }

    public int getXp() {
        return xp;
    }

    public void gainXp(int amount) {
        this.xp += amount;
        // xpForNextLevel reflète le niveau ACTUEL (avant la boucle de level-up
        // ci-dessous) :
        // si ce gain déclenche une montée de niveau, le ratio xp/xpForNextLevel calculé
        // côté
        // client peut dépasser 1 pour cet unique message — sans conséquence, il se
        // corrige au
        // prochain XpGained/GamePlayerStats une fois le nouveau niveau atteint.
        int xpForCurrentLevel = LevelCatalogHolder.xpRequiredForLevel(level);
        int xpForNextLevel = level < LevelCatalogHolder.maxLevel()
                ? LevelCatalogHolder.xpRequiredForLevel(level + 1)
                : xpForCurrentLevel;
        character.send(new XpGained(amount, xp, xpForCurrentLevel, xpForNextLevel));

        while (level < LevelCatalogHolder.maxLevel() && xp >= LevelCatalogHolder.xpRequiredForLevel(level + 1)) {
            applyLevelUp();
        }

        DomainEventPublisher.publish(new CharacterGainedXp(character, amount));
    }

    public void applyLevelUp() {
        level++;

        int newMaxHealth = character.getClassSystem().getCharacterClass()
                .maxHealth(character.getAttribute(Attribute.CON), level);
        int hpGain = newMaxHealth - character.getResourceSystem().getMaxHealth();
        character.getResourceSystem().setMaxHealth(newMaxHealth);
        character.getResourceSystem().setCurrentHealth(character.getResourceSystem().getCurrentHealth() + hpGain);

        int newMaxMana = character.getClassSystem().getCharacterClass().maxMana(character.getAttribute(Attribute.MEN),
                level);
        int manaGain = newMaxMana - character.getResourceSystem().getMaxMana();
        character.getResourceSystem().setMaxMana(newMaxMana);
        character.getResourceSystem().setCurrentMana(character.getResourceSystem().getCurrentMana() + manaGain);

        character.recomputeStats();

        character.broadcast(new PlayerLeveledUp(character.getName(), level), null);
        DomainEventPublisher.publish(new CharacterLeveledUp(character, level, hpGain));
    }
}
