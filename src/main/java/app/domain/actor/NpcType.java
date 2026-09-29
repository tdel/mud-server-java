package app.domain.actor;

/**
 * Rôle d'un PNJ, tel que déclaré dans {@code data/npc/*.xml} ({@code <type>}) —
 * purement descriptif côté serveur pour l'instant : le client s'en sert pour
 * choisir l'apparence du PNJ (un {@code GUARD} est un humain en armure lourde,
 * bouclier et épée courte). Un PNJ sans type garde l'apparence générique.
 */
public enum NpcType {
    GUARD
}
