package app.network.message.ingame;

import java.util.Set;

import app.domain.item.WeaponType;
import app.network.OutputJsonMessage;

// Sort refusé : l'arme équipée n'est pas d'une famille exigée (Power Strike :
// épée/masse, Mortal Blow : dague, Power Shot : arc).
public record SkillWeaponRequired(String skillName, Set<WeaponType> weaponTypes) implements OutputJsonMessage {
}
