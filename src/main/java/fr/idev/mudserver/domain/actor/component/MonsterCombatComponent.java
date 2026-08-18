package fr.idev.mudserver.domain.actor.component;

public class MonsterCombatComponent {

    public String naturalDamageDice;
    public Integer naturalArmorClass;

    public MonsterCombatComponent(String naturalDamageDice, Integer naturalArmorClass) {
        this.naturalDamageDice = naturalDamageDice;
        this.naturalArmorClass = naturalArmorClass;
    }
}
