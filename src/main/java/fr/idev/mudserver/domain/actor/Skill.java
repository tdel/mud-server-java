package fr.idev.mudserver.domain.actor;

public enum Skill {
    ACROBATICS(Attribute.DEXTERITY), ANIMAL_HANDLING(Attribute.WISDOM), ARCANA(Attribute.INTELLIGENCE), ATHLETICS(
            Attribute.STRENGTH), DECEPTION(Attribute.CHARISMA), HISTORY(Attribute.INTELLIGENCE), INSIGHT(
                    Attribute.WISDOM), INTIMIDATION(Attribute.CHARISMA), INVESTIGATION(
                            Attribute.INTELLIGENCE), MEDICINE(Attribute.WISDOM), NATURE(
                                    Attribute.INTELLIGENCE), PERCEPTION(Attribute.WISDOM), PERFORMANCE(
                                            Attribute.CHARISMA), PERSUASION(Attribute.CHARISMA), RELIGION(
                                                    Attribute.INTELLIGENCE), SLEIGHT_OF_HAND(
                                                            Attribute.DEXTERITY), STEALTH(
                                                                    Attribute.DEXTERITY), SURVIVAL(Attribute.WISDOM);

    private final Attribute governingAttribute;

    Skill(Attribute governingAttribute) {
        this.governingAttribute = governingAttribute;
    }

    public Attribute getGoverningAttribute() {
        return governingAttribute;
    }

    public String label() {
        return switch (this) {
            case ACROBATICS -> "Acrobatics";
            case ANIMAL_HANDLING -> "Animal Handling";
            case ARCANA -> "Arcana";
            case ATHLETICS -> "Athletics";
            case DECEPTION -> "Deception";
            case HISTORY -> "History";
            case INSIGHT -> "Insight";
            case INTIMIDATION -> "Intimidation";
            case INVESTIGATION -> "Investigation";
            case MEDICINE -> "Medicine";
            case NATURE -> "Nature";
            case PERCEPTION -> "Perception";
            case PERFORMANCE -> "Performance";
            case PERSUASION -> "Persuasion";
            case RELIGION -> "Religion";
            case SLEIGHT_OF_HAND -> "Sleight of Hand";
            case STEALTH -> "Stealth";
            case SURVIVAL -> "Survival";
        };
    }
}
