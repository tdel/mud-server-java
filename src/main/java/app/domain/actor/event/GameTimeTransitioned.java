package app.domain.actor.event;

import app.domain.world.DayPhase;

public record GameTimeTransitioned(DayPhase phase, int hour, int minute) {
}
