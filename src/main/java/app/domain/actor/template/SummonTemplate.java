package app.domain.actor.template;

import java.util.List;

import app.domain.actor.CreatureRace;

// Serviteur invocable (data/summons/summons.xml) : un SummonLevel par level de
// la compétence d'invocation. model : modèle 3D côté client (cat, panther,
// undead). special : capacité propre (cf. SummonSpecial), null sinon.
public record SummonTemplate(String id, String name, CreatureRace race, String model, SummonSpecial special,
        int lifetimeSeconds, double speed, int atkSpd, List<SummonLevel> levels) {

    // Valeurs de base du PNJ L2 (avant CON/STR/niveau...), cf. SummonInstance.
    public record SummonLevel(int level, double hp, double pAtk, double mAtk, double pDef, double mDef) {
    }

    // RECHARGE_OWNER : rend régulièrement des PM à son maître (Kat) ; STORM :
    // tempête magique régulière sur sa cible (Mew) ; DRAIN : vole de la vie en
    // frappant (Corrupted Man).
    public enum SummonSpecial {
        RECHARGE_OWNER, STORM, DRAIN
    }

    // Level de compétence -> stats (le dernier level connu au-delà).
    public SummonLevel levelFor(int skillLevel) {
        return levels.get(Math.clamp(skillLevel, 1, levels.size()) - 1);
    }
}
