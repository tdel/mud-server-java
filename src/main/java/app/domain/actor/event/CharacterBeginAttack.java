package app.domain.actor.event;

import app.domain.actor.AbstractCharacter;

// stopsAttacker : l'attaque immobilise l'attaquant (corps-à-corps, CombatSystem). Faux
// pour un sort (SkillSystem) : le lanceur est déjà arrêté au début de l'incantation
// (SkillCastEngine.beginCast), et l'impact d'un projectile arrive alors qu'il a pu se
// remettre en marche — l'arrêter à ce moment le figeait en pleine course.
public record CharacterBeginAttack(AbstractCharacter attacker, AbstractCharacter defender, boolean stopsAttacker) {
}
