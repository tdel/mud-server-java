package app.domain;

// Famille d'un effet à dégâts périodiques, pour les soins ciblés (Cure Poison
// ne retire que les poisons, Remedy que les saignements).
public enum PeriodicKind {
    POISON, BLEED;

    public boolean curedBy(CureKind cure) {
        return cure == CureKind.ALL || cure.name().equals(name());
    }
}
