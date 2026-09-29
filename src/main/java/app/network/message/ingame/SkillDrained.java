package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

// Vampiric Touch : PV rendus au lanceur (diffusé à portée, lanceur compris),
// casterHealth/casterMaxHealth après le drain.
public record SkillDrained(UUID casterId, String casterName, UUID targetId, String skillName, int amount,
        int casterHealth, int casterMaxHealth) implements OutputJsonMessage {
}
