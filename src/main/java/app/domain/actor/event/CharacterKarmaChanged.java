package app.domain.actor.event;

import app.domain.actor.instance.CharacterInstance;

public record CharacterKarmaChanged(CharacterInstance character, int newKarma) {
}
