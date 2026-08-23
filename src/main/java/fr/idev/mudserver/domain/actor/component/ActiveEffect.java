package fr.idev.mudserver.domain.actor.component;

import java.time.Instant;
import java.util.UUID;

import fr.idev.mudserver.domain.actor.ModifiedStat;

public record ActiveEffect(UUID spellId, String spellName, ModifiedStat stat, int amount, Instant expiresAt) {
}
