package app.domain.actor.system;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.event.CharacterGainedXp;
import app.domain.actor.event.CharacterLeveledUp;
import app.domain.actor.event.DomainEventPublisher;
import app.domain.actor.instance.PlayerInstance;
import app.game.catalog.LevelCatalogHolder;
import app.network.message.ingame.PlayerLeveledUp;
import app.network.message.ingame.XpGained;

public final class LevelingSystem {

    private final AbstractCharacter character;
    private int level;
    private int xp;

    public LevelingSystem(AbstractCharacter character, int level, int xp) {
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
        // gainXp n'est appelé que sur des PlayerInstance (LootSystem) : seuls les
        // joueurs gagnent de l'XP et montent de niveau.
        PlayerInstance player = (PlayerInstance) character;

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
            applyLevelUp(player);
        }

        DomainEventPublisher.publish(new CharacterGainedXp(player, amount));
    }

    public void applyLevelUp(PlayerInstance player) {
        level++;

        int previousMaxHealth = character.getResourceSystem().getBaseMaxHealth();
        player.getClassSystem().refreshVitals();
        int hpGain = character.getResourceSystem().getBaseMaxHealth() - previousMaxHealth;

        character.getStatSystem().recomputeStats(character.getAttributeSystem().getAttributes(), level,
                player.getInventorySystem(), player.getClassSystem().getCharacterClass().baseStats());

        character.broadcast(new PlayerLeveledUp(character.getName(), level), null);
        DomainEventPublisher.publish(new CharacterLeveledUp(player, level, hpGain));
    }
}
