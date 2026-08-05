package fr.idev.mudserver.domain;

public enum Gender {
    MAN, WOMAN;

    public String label() {
        return switch (this) {
            case MAN -> "Man";
            case WOMAN -> "Woman";
        };
    }
}
