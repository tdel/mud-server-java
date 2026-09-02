package app.domain;

import app.domain.actor.ModifiedStat;

public record StatModifier(ModifiedStat stat, int value, StatOperator operator) {
}
