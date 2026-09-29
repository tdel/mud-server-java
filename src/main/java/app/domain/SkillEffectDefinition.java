package app.domain;

import java.util.List;

// chance : probabilité (%) qu'un effet secondaire se pose sur une cible touchée,
// 100 = toujours (Ice Bolt : 60). dotInterval : période (s) des dégâts d'un
// effet de poison, 0 si l'effet n'en inflige pas — le montant par période est
// le power(level) du sort (cf. SkillSystem.castModifier).
public record SkillEffectDefinition(String name, int time, int power, EffectCategory type, List<StatModifier> effect,
        int chance, int dotInterval) {
}
