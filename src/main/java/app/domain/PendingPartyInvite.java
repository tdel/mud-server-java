package app.domain;

import app.domain.actor.instance.CharacterInstance;

public record PendingPartyInvite(Party party, CharacterInstance inviter, long sentAtMillis) {
}
