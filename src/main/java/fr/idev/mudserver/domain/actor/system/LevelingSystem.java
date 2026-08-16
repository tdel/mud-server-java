package fr.idev.mudserver.domain.actor.system;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.component.AppearanceComponent;
import fr.idev.mudserver.domain.actor.component.LevelingComponent;
import fr.idev.mudserver.domain.actor.event.CharacterGainedXp;
import fr.idev.mudserver.domain.actor.event.CharacterLeveledUp;
import fr.idev.mudserver.domain.actor.event.DomainEventPublisher;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

@Service
public class LevelingSystem {

    private final AttributeSystem attributeSystem;
    private final CombatSystem combatSystem;

    public LevelingSystem(AttributeSystem attributeSystem, CombatSystem combatSystem) {
        this.attributeSystem = attributeSystem;
        this.combatSystem = combatSystem;
    }

    public void gainXp(CharacterInstance character, int amount) {
        character.updateComponent(LevelingComponent.class,
                current -> new LevelingComponent(current.level(), current.xp() + amount));
        DomainEventPublisher.publish(new CharacterGainedXp(character, amount));
    }

    public int getProficiencyBonus(CharacterInstance character) {
        return 2 + Math.floorDiv(character.component(LevelingComponent.class).level() - 1, 4);
    }

    public int hitDieRecovery(CharacterInstance character) {
        int hitDie = character.component(AppearanceComponent.class).characterClass().hitDie();
        return Math.max(1, hitDie / 2 + 1 + attributeSystem.getModifier(character, Attribute.CONSTITUTION));
    }

    public void applyLevelUp(CharacterInstance character) {
        int hpGain = hitDieRecovery(character);
        int newLevel = character.updateComponent(LevelingComponent.class,
                current -> new LevelingComponent(current.level() + 1, current.xp())).level();
        combatSystem.increaseMaxHealth(character, hpGain);
        DomainEventPublisher.publish(new CharacterLeveledUp(character, newLevel, hpGain));
    }
}
