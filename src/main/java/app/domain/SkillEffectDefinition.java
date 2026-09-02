package app.domain;

import java.util.List;

public record SkillEffectDefinition(String name, int time, int power, EffectCategory type, List<StatModifier> effect) {
}
