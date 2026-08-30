package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

/**
 * Diffusé aux membres déjà présents dans le groupe quand quelqu'un le rejoint
 * (voir PartyAccept) — vitaux inclus pour la même raison que
 * PartyJoined.MemberView (symétrique, côté membres déjà présents cette fois) :
 * sans ça, le nouveau membre apparaîtrait dans le roster de chacun avec un
 * PV/mana à 0 jusqu'à son prochain événement de vitaux.
 */
public record PartyMemberJoined(UUID memberId, String memberName, int currentHealth, int maxHealth, int currentMana,
        int maxMana) implements OutputJsonMessage {

}
