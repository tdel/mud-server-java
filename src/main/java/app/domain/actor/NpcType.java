package app.domain.actor;

/**
 * Rôle d'un PNJ, tel que déclaré dans {@code data/npc/*.xml} ({@code <type>}).
 * Le client s'en sert pour choisir l'apparence du PNJ (un {@code GUARD} est un
 * humain en armure lourde, bouclier et épée courte ; un {@code SKILL_LEARNER}
 * un maître en robe et bâton). {@code SKILL_LEARNER} a aussi un rôle serveur :
 * seul un tel PNJ accepte les commandes {@code skill-list}/{@code learn-skill}.
 * Un PNJ sans type garde l'apparence générique.
 */
public enum NpcType {
    GUARD, SKILL_LEARNER
}
