package fr.idev.mudserver.domain;

import java.util.UUID;

public record Character(UUID id, UUID accountId, String name, UUID currentRoomId, Race race, int currentHealth,
        int maxHealth, int currentMana, int maxMana, int strength, int dexterity, int constitution, int intelligence,
        int wisdom, int charisma) {
}
