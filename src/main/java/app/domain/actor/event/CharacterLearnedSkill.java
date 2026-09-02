package app.domain.actor.event;

import app.domain.ActiveSkill;
import app.domain.actor.instance.CharacterInstance;

public record CharacterLearnedSkill(CharacterInstance character, ActiveSkill activeSkill, int newLevel,
        int previousLevel) {
}
