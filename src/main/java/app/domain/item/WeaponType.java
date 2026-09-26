package app.domain.item;

// Famille d'arme, authored par arme dans weapons.xml (<weaponType>) : décide de
// la tenue (une/deux mains) et du rendu côté client (modèle d'arme, animation
// d'attaque). Découpage façon Lineage 2 (SWORD/BIGSWORD/BLUNT/BIGBLUNT/DAGGER/
// POLE/BOW), plus AXE/STAFF/WAND séparés de BLUNT pour que le client puisse les
// dessiner différemment.
public enum WeaponType {
    SWORD, BIG_SWORD, DAGGER, BLUNT, BIG_BLUNT, AXE, POLE, STAFF, WAND, BOW;

    public boolean twoHanded() {
        return switch (this) {
            case BIG_SWORD, BIG_BLUNT, POLE, STAFF, BOW -> true;
            default -> false;
        };
    }
}
