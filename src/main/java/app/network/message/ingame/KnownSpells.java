package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.domain.SpellEffectType;
import app.network.OutputJsonMessage;

public record KnownSpells(List<Entry> spells) implements OutputJsonMessage {

    public record Entry(UUID id, String name, int tier, String description, int manaCost, int cooldownSeconds,
            int range, SpellEffectType effect, int durationSeconds, boolean granted) {
    }

}
