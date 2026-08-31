package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.domain.SkillEffectType;
import app.network.OutputJsonMessage;

public record KnownSkills(List<Entry> skills) implements OutputJsonMessage {

    public record Entry(UUID id, String name, int tier, String description, int manaCost, int cooldownSeconds,
            int range, SkillEffectType effect, int durationSeconds, boolean granted) {
    }

}
