package app.domain;

import app.domain.actor.instance.PlayerInstance;

public record PendingPartyInvite(Party party, PlayerInstance inviter, long sentAtMillis) {
}
