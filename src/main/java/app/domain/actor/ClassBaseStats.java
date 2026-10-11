package app.domain.actor;

import java.util.Map;

import app.domain.item.EquipmentSlot;

// Valeurs "à nu" d'une classe L2 H5 (staticData de stats/chars/baseStats) :
// p.atk/m.atk du poing, vitesse d'attaque et critique à mains nues, vitesse de
// course, et défenses de base par emplacement — remplacées par celles de la
// pièce portée (FuncPDefMod/FuncMDefMod). Les sous-vêtements et la cape n'ont
// pas d'emplacement dans ce jeu : leur défense nue compte toujours
// (pDefAlwaysBare). Les professions partagent celles de leur classe de base.
public record ClassBaseStats(double pAtk, double mAtk, int atkSpd, double critRate, double runSpeed,
        Map<EquipmentSlot, Double> pDefBySlot, double pDefAlwaysBare, Map<EquipmentSlot, Double> mDefBySlot) {

    public ClassBaseStats {
        pDefBySlot = Map.copyOf(pDefBySlot);
        mDefBySlot = Map.copyOf(mDefBySlot);
    }

    public double barePDef(EquipmentSlot slot) {
        return pDefBySlot.getOrDefault(slot, 0.0);
    }

    public double bareMDef(EquipmentSlot slot) {
        return mDefBySlot.getOrDefault(slot, 0.0);
    }

    public double totalBarePDef() {
        return pDefBySlot.values().stream().mapToDouble(Double::doubleValue).sum() + pDefAlwaysBare;
    }

    public double totalBareMDef() {
        return mDefBySlot.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}
