package app.domain.actor.template;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import app.domain.ActiveEffect;
import app.domain.ActiveSkill;
import app.domain.PassiveSkill;
import app.domain.map.Position;
import app.domain.actor.AbstractNpc;
import app.domain.actor.instance.NpcSellerInstance;

public record NpcTemplate(UUID id, String name, UUID mapTemplateId, Position position, String description,
        AbstractNpc.NpcDialogue dialogue, NpcSellerInstance.NpcShop shop, int level, Set<ActiveSkill> knownSkills,
        Set<PassiveSkill> knownPassiveSkills, List<ActiveEffect> activeEffects) {
}
