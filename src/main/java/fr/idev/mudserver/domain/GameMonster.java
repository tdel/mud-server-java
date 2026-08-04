package fr.idev.mudserver.domain;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Contrairement à {@link Item}, qui délègue {@code getName()}/{@code getType()}
 * à son template en lecture à chaque appel, {@code GameMonster} copie au moment
 * de sa construction (voir {@code MonsterService.warmMonsters}) le nom, les
 * attributs et les PV max du {@link MonsterTemplate} résolu dans les champs
 * hérités de {@link GameCharacter} — ces champs sont la représentation partagée
 * unique utilisée par {@link GamePlayer}/{@link GameMonster}/ {@link GameNpc}
 * ({@code getModifier}/{@code getCurrentHealth} doivent fonctionner pareil pour
 * les trois, pas de délégation différente par sous-type). Le template reste
 * néanmoins attaché pour exposer {@link #getDescription()} par délégation, un
 * champ propre au flavor text qui n'a pas sa place sur {@code GameCharacter}.
 */
public final class GameMonster extends GameCharacter {

    private final UUID templateId;
    private final UUID roomId;

    private MonsterTemplate template;

    public GameMonster(UUID id, String name, UUID templateId, UUID roomId, Map<Attribute, Integer> attributes,
            int maxHealth) {
        super(id, name, attributes, maxHealth, maxHealth);
        this.templateId = templateId;
        this.roomId = roomId;
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
        if (!(o instanceof GameMonster other)) {
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
