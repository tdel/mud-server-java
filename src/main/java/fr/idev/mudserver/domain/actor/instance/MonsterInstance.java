package fr.idev.mudserver.domain.actor.instance;

import java.util.UUID;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.component.BehaviorComponent;
import fr.idev.mudserver.domain.actor.component.LootComponent;
import fr.idev.mudserver.domain.actor.template.MonsterTemplate;
import fr.idev.mudserver.domain.world.RoomInstance;

public final class MonsterInstance extends AbstractCharacter {

    private final MonsterTemplate template;

    public MonsterInstance(UUID id, MonsterTemplate template, RoomInstance roomInstance) {
        super(id, template.getName(), template.getAttributes(), template.getMaxHealth(), template.getMaxHealth(),
                template.getSpeed());
        this.template = template;
        attachComponent(BehaviorComponent.idle());
        attachComponent(new LootComponent(template.getLootTable(), template.getXpReward(), template.getGoldReward()));
        setCurrentRoom(roomInstance);
    }

    public MonsterTemplate getTemplate() {
        return template;
    }

    public String getDescription() {
        return template.getDescription();
    }

    public String getNaturalDamageDice() {
        return template.getNaturalDamageDice();
    }

    public int getPresenceRadius() {
        return template.getPresenceRadius();
    }

}
