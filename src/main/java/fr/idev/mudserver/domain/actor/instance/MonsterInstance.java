package fr.idev.mudserver.domain.actor.instance;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.component.BehaviorComponent;
import fr.idev.mudserver.domain.actor.system.AttributeSystem;
import fr.idev.mudserver.domain.actor.template.MonsterTemplate;

public final class MonsterInstance extends AbstractCharacter {

    private final UUID templateId;
    private final UUID roomId;

    private MonsterTemplate template;

    public MonsterInstance(UUID id, String name, UUID templateId, UUID roomId, Map<Attribute, Integer> attributes,
            int maxHealth) {
        super(id, name, attributes, maxHealth, maxHealth, 5);
        this.templateId = templateId;
        this.roomId = roomId;
        attachComponent(BehaviorComponent.idle());
    }

    public void attachTemplate(MonsterTemplate template) {
        this.template = template;
    }

    public MonsterTemplate getTemplate() {
        return template;
    }

    public String getDescription() {
        return requireTemplate().getDescription();
    }

    public String getNaturalDamageDice() {
        return requireTemplate().getNaturalDamageDice();
    }

    public int getPresenceRadius() {
        return requireTemplate().getPresenceRadius();
    }

    public int getLevel() {
        return requireTemplate().getLevel();
    }

    private MonsterTemplate requireTemplate() {
        if (template == null) {
            throw new IllegalStateException("GameMonster " + getId() + " has no MonsterTemplate attached");
        }
        return template;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public UUID getRoomId() {
        return roomId;
    }

}
