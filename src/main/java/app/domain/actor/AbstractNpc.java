package app.domain.actor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import app.domain.world.MapInstance;
import app.domain.actor.system.DialogueSystem;
import app.domain.actor.template.NpcTemplate;
import app.game.catalog.LevelCatalogHolder;

public class AbstractNpc extends AbstractCharacter {

    private static final int NOMINAL_HEALTH = 1;

    private final NpcTemplate template;
    private final DialogueSystem dialogueSystem;

    public AbstractNpc(UUID id, NpcTemplate template, MapInstance map) {
        super(id, template.name(), neutralAttributes(), NOMINAL_HEALTH, NOMINAL_HEALTH,
                template.knownSkills().stream().collect(Collectors.toMap(skill -> skill, skill -> 1)),
                template.knownPassiveSkills().stream().collect(Collectors.toMap(skill -> skill, skill -> 1)),
                template.activeEffects(), Map.of(ModifiedStat.SPEED, 0), true, 0, 0, List.of(),
                LevelCatalogHolder.maxLevel(), 0);
        this.template = Objects.requireNonNull(template);
        this.dialogueSystem = new DialogueSystem(template.dialogue());
        setTitle(template.title());
        getMotionSystem().setCurrentMap(Objects.requireNonNull(map));
    }

    public DialogueSystem getDialogueSystem() {
        return dialogueSystem;
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
    public String toString() {
        return "GameNpc[id=" + getId() + ", name=" + getName() + ", mapId=" + getMotionSystem().getCurrentMap().getId()
                + "]";
    }
}
