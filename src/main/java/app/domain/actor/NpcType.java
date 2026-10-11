package app.domain.actor;

/**
 * Rôle d'un PNJ, tel que déclaré dans {@code data/npc/*.xml} ({@code <type>}).
 * Le client s'en sert pour choisir l'apparence du PNJ (un {@code GUARD} est un
 * humain en armure lourde, bouclier et épée courte ; un {@code SKILL_LEARNER}
 * un maître en robe et bâton ; un {@code MERCHANT} un commerçant en habits de
 * tissu ; un {@code BLACKSMITH} un forgeron en cuir, marteau en main ; un
 * {@code PRIEST} un prêtre d'Einhasad en robe blanche ; un {@code GATEKEEPER}
 * une gardienne des portails en robe pourpre ; un {@code WAREHOUSE_KEEPER} un
 * magasinier en cuir ; un {@code MAGISTER} un marchand de magie en robe
 * violette ; un {@code WEAPON_MERCHANT}/{@code ARMOR_MERCHANT} un marchand
 * d'armes/d'armures ; un {@code CHAMBERLAIN} l'intendant d'un château).
 * {@code SKILL_LEARNER} a aussi un rôle serveur : seul un tel PNJ accepte les
 * commandes {@code skill-list}/{@code learn-skill}. Un PNJ sans type garde
 * l'apparence générique (villageois).
 */
public enum NpcType {
    GUARD, SKILL_LEARNER, MERCHANT, BLACKSMITH, PRIEST, GATEKEEPER, WAREHOUSE_KEEPER, MAGISTER, WEAPON_MERCHANT, ARMOR_MERCHANT, CHAMBERLAIN
}
