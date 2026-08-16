package fr.idev.mudserver.domain.actor.system;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.component.LevelingComponent;
import fr.idev.mudserver.domain.actor.event.CharacterGainedXp;
import fr.idev.mudserver.domain.actor.event.CharacterLeveledUp;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

public final class LevelingSystem {

    private LevelingSystem() {
    }

    public static void gainXp(CharacterInstance character, int amount) {
        character.updateComponent(LevelingComponent.class,
                current -> new LevelingComponent(current.level(), current.xp() + amount));
        DomainEventPublisher.publish(new CharacterGainedXp(character, amount));
    }

    public static int hitDieRecovery(CharacterInstance character) {
        int hitDie = character.getCharacterClass().hitDie();
        return Math.max(1, hitDie / 2 + 1 + character.getModifier(Attribute.CONSTITUTION));
    }

    public static void applyLevelUp(CharacterInstance character) {
        int hpGain = hitDieRecovery(character);
        int newLevel = character.updateComponent(LevelingComponent.class,
                current -> new LevelingComponent(current.level() + 1, current.xp())).level();
        CombatSystem.increaseMaxHealth(character, hpGain);
        DomainEventPublisher.publish(new CharacterLeveledUp(character, newLevel, hpGain));
    }
}
