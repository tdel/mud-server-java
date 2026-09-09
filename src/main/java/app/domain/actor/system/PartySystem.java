package app.domain.actor.system;

import app.domain.Party;
import app.domain.PendingPartyInvite;

public final class PartySystem {

    private Party party;
    private PendingPartyInvite pendingInvite;

    public Party getParty() {
        return party;
    }

    public void setParty(Party party) {
        this.party = party;
    }

    public PendingPartyInvite getPendingInvite() {
        return pendingInvite;
    }

    public void setPendingInvite(PendingPartyInvite pendingInvite) {
        this.pendingInvite = pendingInvite;
    }
}
