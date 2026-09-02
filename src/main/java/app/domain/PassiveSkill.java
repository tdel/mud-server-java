package app.domain;

import java.util.UUID;

import app.domain.item.ItemGrade;

public record PassiveSkill(UUID id, String name, String description, ItemGrade grantsGrade) {
}
