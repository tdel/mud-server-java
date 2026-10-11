package app.domain;

import java.util.List;

// hpCost : PV sacrifiés au lancement (Body To Mind, Soul of Sagittarius) ;
// mpPerTick : PM consommés toutes les 5 s par un toggle ; hotPower : soin par
// seconde de l'effet secondaire (Greater Heal) ; modifiers : modificateurs
// littéraux de ce level (buffs/debuffs/toggles de profession), qui remplacent
// alors la règle historique power(level) x poids de l'effet.
public record SkillLevel(int level, int mana, int power, int hpCost, int mpPerTick, int hotPower,
        List<StatModifier> modifiers) {

    public SkillLevel(int level, int mana, int power) {
        this(level, mana, power, 0, 0, 0, List.of());
    }
}
