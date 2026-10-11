package app.domain;

// CURE : retire de la cible ses effets périodiques (Cure Poison, Remedy).
// TOGGLE : effet sur soi activé/désactivé à chaque lancement, qui consomme des
// PM en continu (Vicious Stance). SUMMON : invoque un serviteur ; CUBIC : un
// cubique. RESURRECT : ramène un joueur mort à la vie. RECHARGE : rend des PM
// (Body To Mind, Servitor Recharge). AGGRO : force les monstres à attaquer le
// lanceur (Aggression) ; PEACE : les fait cesser le combat. BLINK : téléporte
// le lanceur dans le dos de sa cible (Shadow Step).
public enum SkillEffectType {
    DAMAGE, HEALING, BUFF, DEBUFF, CURE, PASSIVE, TELEPORT, TOGGLE, SUMMON, CUBIC, RESURRECT, RECHARGE, AGGRO, PEACE, BLINK
}
