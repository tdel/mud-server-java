package app.domain.actor.event;

import java.util.List;

import app.domain.actor.Subclass;
import app.domain.actor.instance.CharacterInstance;

public record SubclassChoiceAvailable(CharacterInstance character, int tier, List<Subclass> options) {
}
