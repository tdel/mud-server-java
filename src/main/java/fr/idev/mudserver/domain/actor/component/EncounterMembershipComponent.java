package fr.idev.mudserver.domain.actor.component;

import java.util.UUID;

public class EncounterMembershipComponent {

    public UUID encounterId;

    public EncounterMembershipComponent(UUID encounterId) {
        this.encounterId = encounterId;
    }
}
