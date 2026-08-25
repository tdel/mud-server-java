package app.domain.actor.event;

import app.domain.actor.instance.CharacterInstance;

public record CharacterRegenerated(CharacterInstance character, int hpRestored, int manaRestored) {
}
