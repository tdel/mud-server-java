package app.domain.actor.template;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import app.domain.ActiveEffect;
import app.domain.ActiveSkill;
import app.domain.PassiveSkill;
import app.domain.map.Position;
import app.domain.actor.system.DialogueSystem;
import app.domain.actor.system.SellSystem;

public record NpcTemplate(UUID id, String name, String title, UUID mapTemplateId, Position position,
        DialogueSystem.NpcDialogue dialogue, SellSystem.NpcShop shop, int level, Set<ActiveSkill> knownSkills,
        Set<PassiveSkill> knownPassiveSkills, List<ActiveEffect> activeEffects) {
}
