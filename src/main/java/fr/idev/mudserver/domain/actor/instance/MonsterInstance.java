package fr.idev.mudserver.domain.actor.instance;

import java.util.UUID;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.component.AggroComponent;
import fr.idev.mudserver.domain.actor.component.BehaviorComponent;
import fr.idev.mudserver.domain.actor.component.LootComponent;
import fr.idev.mudserver.domain.actor.component.PositionComponent;
import fr.idev.mudserver.domain.actor.template.MonsterTemplate;
import fr.idev.mudserver.domain.world.RoomInstance;

public final class MonsterInstance extends AbstractCharacter {

    private final MonsterTemplate template;

    public MonsterInstance(UUID id, MonsterTemplate template, RoomInstance roomInstance) {
        super(id, template.name(), template.attributes(), template.maxHealth(), template.maxHealth(), template.speed());
        this.template = template;
        attachComponent(BehaviorComponent.idle());
        attachComponent(new LootComponent(template.lootTable(), template.xpReward(), template.goldReward()));
        attachComponent(new AggroComponent(template.aggroRadius()));
        attachComponent(new PositionComponent(roomInstance, null)); // missing coordinate ??
    }

    public MonsterTemplate getTemplate() {
        return template;
    }

    public String getDescription() {
        return template.description();
    }

    public String getNaturalDamageDice() {
        return template.naturalDamageDice();
    }

}
