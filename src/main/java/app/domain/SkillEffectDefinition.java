package app.domain;

import java.util.List;

// chance : probabilité (%) qu'un effet secondaire se pose sur une cible touchée,
// 100 = toujours (Ice Bolt : 60). dotInterval : période (s) des dégâts d'un
// effet de poison/saignement (dotKind), 0 si l'effet n'en inflige pas — le
// montant par période est le power(level) du sort (cf. SkillSystem.castModifier).
// hotInterval : période (s) d'un soin régulier (SkillLevel.hotPower). status :
// altération d'état posée (STUN...), null sinon.
public record SkillEffectDefinition(String name, int time, int power, EffectCategory type, List<StatModifier> effect,
        int chance, int dotInterval, PeriodicKind dotKind, int hotInterval, AbnormalStatus status) {
}
