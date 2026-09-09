package app.domain.actor.event;

import app.domain.ActiveSkill;
import app.domain.actor.instance.PlayerInstance;

public record CharacterLearnedSkill(PlayerInstance character, ActiveSkill activeSkill, int newLevel,
        int previousLevel) {
}
