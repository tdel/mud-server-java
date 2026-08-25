package app.domain.actor.component;

import java.time.Instant;
import java.util.UUID;

import app.domain.actor.ModifiedStat;

public record ActiveEffect(UUID spellId, String spellName, ModifiedStat stat, int amount, Instant expiresAt) {
}
