package fr.idev.mudserver.domain;

/**
 * Type d'effet appliqué par {@link ConsumableItem#consume}. Une seule valeur
 * pour l'instant (potions de soin) — point d'extension explicite pour de futurs
 * consommables (poison, etc.), chacun ajoutant sa propre branche dans
 * {@link ConsumableItem#consume}.
 */
public enum ConsumableEffect {
    HEALING
}
