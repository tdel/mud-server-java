package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

public record CharacterRegenerated(CharacterInstance character, int hpRestored, int manaRestored) {
}
