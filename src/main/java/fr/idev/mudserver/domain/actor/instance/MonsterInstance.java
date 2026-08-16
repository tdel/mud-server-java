package fr.idev.mudserver.domain.actor.instance;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.system.AiSystem;
import fr.idev.mudserver.domain.actor.template.MonsterTemplate;

public final class MonsterInstance extends AbstractCharacter {

    private final UUID templateId;
    private final UUID roomId;

    private MonsterTemplate template;

    public MonsterInstance(UUID id, String name, UUID templateId, UUID roomId, Map<Attribute, Integer> attributes,
            int maxHealth) {
        super(id, name, attributes, maxHealth, maxHealth);
        this.templateId = templateId;
        this.roomId = roomId;
        AiSystem.attach(this);
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

    @Override
    public int getSpeed() {
        return requireTemplate().getSpeed();
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MonsterInstance other)) {
            return false;
        }
        return getCurrentHealth() == other.getCurrentHealth() && getMaxHealth() == other.getMaxHealth()
                && Objects.equals(getId(), other.getId()) && Objects.equals(getName(), other.getName())
                && Objects.equals(templateId, other.templateId) && Objects.equals(roomId, other.roomId)
                && Objects.equals(getAttributes(), other.getAttributes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), templateId, roomId, getAttributes(), getCurrentHealth(),
                getMaxHealth());
    }

    @Override
    public String toString() {
        return "GameMonster[id=" + getId() + ", name=" + getName() + ", templateId=" + templateId + ", roomId=" + roomId
                + ", currentHealth=" + getCurrentHealth() + ", maxHealth=" + getMaxHealth() + "]";
    }
}
