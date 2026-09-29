package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.domain.actor.CharacterClass;
import app.domain.actor.Subclass;
import app.domain.actor.instance.PlayerInstance;
import app.network.OutputJsonMessage;

/**
 * Diffusé aux membres déjà présents dans le groupe quand quelqu'un le rejoint
 * (voir PartyAccept) — niveau/classe, vitaux et effets actifs inclus pour la
 * même raison que PartyJoined.MemberView (symétrique, côté membres déjà
 * présents cette fois) : sans ça, le nouveau membre apparaîtrait dans le roster
 * de chacun sans niveau ni classe, avec un PV/mana à 0 et sans ses buffs
 * jusqu'à son prochain événement.
 */
public record PartyMemberJoined(UUID memberId, String memberName, int level, CharacterClass characterClass,
        Subclass subclass, int currentHealth, int maxHealth, int currentMana, int maxMana,
        List<PartyEffectView> effects) implements OutputJsonMessage {

    public static PartyMemberJoined of(PlayerInstance member) {
        return new PartyMemberJoined(member.getId(), member.getName(), member.getLevelingSystem().getLevel(),
                member.getClassSystem().getCharacterClass(), member.getClassSystem().getCurrentSubclass(),
                member.getResourceSystem().getCurrentHealth(), member.getResourceSystem().getMaxHealth(),
                member.getResourceSystem().getCurrentMana(), member.getResourceSystem().getMaxMana(),
                PartyEffectView.listOf(member));
    }
}
