package fr.idev.mudserver.domain.actor.component;

public class HealthComponent {

    public int currentHealth;
    public int maxHealth;

    public HealthComponent(int currentHealth, int maxHealth) {
        this.currentHealth = currentHealth;
        this.maxHealth = maxHealth;
    }
}
