package app.network.message.ingame;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import app.domain.SkillElement;
import app.domain.SkillEffectType;
import app.domain.SkillTargetType;
import app.domain.item.WeaponType;
import app.network.OutputJsonMessage;

// Sorts actifs (appris ou octroyés par un objet équipé) puis compétences
// passives (skillType PASSIVE : Weapon Mastery, Expertise Grade... — ni mana,
// ni recharge, jamais posées sur la barre de raccourcis). weaponTypes : familles
// d'arme exigées, vide si aucune.
public record KnownSkills(List<Entry> skills) implements OutputJsonMessage {

    public record Entry(UUID id, String name, int level, int manaCost, int cooldownSeconds, int range,
            SkillEffectType skillType, int durationSeconds, boolean granted, String description, int maxLevel,
            int castTimeMs, SkillTargetType target, SkillElement element, Set<WeaponType> weaponTypes) {
    }

}
