package app.domain.actor.event;

import app.domain.ActiveSkill;
import app.domain.actor.AbstractCharacter;

public record SkillCastBegin(AbstractCharacter caster, ActiveSkill activeSkill, int level, AbstractCharacter target) {
}
