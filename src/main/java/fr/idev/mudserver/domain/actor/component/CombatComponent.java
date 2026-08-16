package fr.idev.mudserver.domain.actor.component;

import fr.idev.mudserver.domain.actor.AbstractCharacter;

public record CombatComponent(int currentHealth, int maxHealth, AbstractCharacter target) {
}
