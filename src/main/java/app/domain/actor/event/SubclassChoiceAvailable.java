package app.domain.actor.event;

import java.util.List;

import app.domain.actor.Subclass;
import app.domain.actor.instance.PlayerInstance;

public record SubclassChoiceAvailable(PlayerInstance character, int tier, List<Subclass> options) {
}
