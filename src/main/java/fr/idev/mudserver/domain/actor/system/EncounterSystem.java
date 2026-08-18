package fr.idev.mudserver.domain.actor.system;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.component.EncounterMembershipComponent;
import fr.idev.mudserver.domain.combat.CombatEncounter;
import fr.idev.mudserver.domain.world.RoomInstance;

@Service
public class EncounterSystem {

    private final Map<UUID, CombatEncounter> encounters = new ConcurrentHashMap<>();

    public CombatEncounter createEncounter(RoomInstance room) {
        CombatEncounter encounter = new CombatEncounter(room);
        encounters.put(encounter.getId(), encounter);
        return encounter;
    }

    public CombatEncounter getEncounter(AbstractCharacter character) {
        return character.findComponent(EncounterMembershipComponent.class).map(membership -> membership.encounterId)
                .map(encounters::get).orElse(null);
    }

    public boolean isInCombat(AbstractCharacter character) {
        return getEncounter(character) != null;
    }

    public void join(AbstractCharacter character, CombatEncounter encounter) {
        character.attachComponent(new EncounterMembershipComponent(encounter.getId()));
    }

    public void leave(AbstractCharacter character) {
        character.detachComponent(EncounterMembershipComponent.class);
    }

    public void endEncounter(CombatEncounter encounter) {
        encounters.remove(encounter.getId());
    }
}
