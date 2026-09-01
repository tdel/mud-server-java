package app.domain.actor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import app.domain.world.MapInstance;
import app.domain.actor.template.NpcTemplate;

public class AbstractNpc extends AbstractCharacter {

    private static final int NOMINAL_HEALTH = 1;

    private final NpcTemplate template;

    public AbstractNpc(UUID id, NpcTemplate template, MapInstance map) {
        super(id, template.name(), neutralAttributes(), NOMINAL_HEALTH, NOMINAL_HEALTH, template.knownSkills(),
                template.knownPassiveSkills(), template.activeEffects());
        this.template = Objects.requireNonNull(template);
        setCurrentMap(Objects.requireNonNull(map));
        this.speed = 0;
    }

    public String getDescription() {
        return template.description();
    }

    public int getLevel() {
        return template.level();
    }

    public Optional<NpcDialogue> getDialogue() {
        return Optional.ofNullable(template.dialogue());
    }

    protected NpcTemplate getTemplate() {
        return template;
    }

    private static Map<Attribute, Integer> neutralAttributes() {
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            attributes.put(attribute, 10);
        }
        return attributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractNpc other)) {
            return false;
        }
        return Objects.equals(getId(), other.getId()) && Objects.equals(getName(), other.getName())
                && Objects.equals(getCurrentMap(), other.getCurrentMap());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), getCurrentMap());
    }

    @Override
    public String toString() {
        return "GameNpc[id=" + getId() + ", name=" + getName() + ", mapId=" + getCurrentMap().getId() + "]";
    }

    public enum NpcDialogueOptionType {
        RESPONSE, SHOP, LEAVE
    }

    public record NpcDialogue(String greeting, List<NpcDialogueOption> options) {

        public Optional<NpcDialogueOption> resolveOption(String input) {
            try {
                int index = Integer.parseInt(input.trim());
                return index >= 1 && index <= options.size() ? Optional.of(options.get(index - 1)) : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
    }

    public record NpcDialogueOption(String label, NpcDialogueOptionType type, String response) {
    }
}
