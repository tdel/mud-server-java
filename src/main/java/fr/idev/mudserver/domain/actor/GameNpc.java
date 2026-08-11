package fr.idev.mudserver.domain.actor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public sealed class GameNpc extends GameCharacter permits GameNpcSeller {

    private static final int NOMINAL_HEALTH = 1;

    private final UUID roomId;
    private final String description;
    private final NpcDialogue dialogue;

    public GameNpc(UUID id, String name, UUID roomId, String description, NpcDialogue dialogue) {
        super(id, name, neutralAttributes(), NOMINAL_HEALTH, NOMINAL_HEALTH);
        this.roomId = roomId;
        this.description = description;
        this.dialogue = dialogue;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public String getDescription() {
        return description;
    }

    public Optional<NpcDialogue> getDialogue() {
        return Optional.ofNullable(dialogue);
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
        if (!(o instanceof GameNpc other)) {
            return false;
        }
        return Objects.equals(getId(), other.getId()) && Objects.equals(getName(), other.getName())
                && Objects.equals(roomId, other.roomId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), roomId);
    }

    @Override
    public String toString() {
        return "GameNpc[id=" + getId() + ", name=" + getName() + ", roomId=" + roomId + "]";
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
