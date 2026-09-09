package app.domain.actor.event;

import app.domain.actor.instance.PlayerInstance;

public record CharacterRegenerated(PlayerInstance character, int hpRestored, int manaRestored) {
}
