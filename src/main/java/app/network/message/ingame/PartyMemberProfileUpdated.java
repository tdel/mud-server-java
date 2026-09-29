package app.network.message.ingame;

import java.util.UUID;

import app.domain.actor.CharacterClass;
import app.domain.actor.Subclass;
import app.network.OutputJsonMessage;

/**
 * Diffusé au reste du groupe quand l'identité affichée d'un membre change
 * (montée de niveau, choix de sous-classe — voir PartyEngine) : la fenêtre de
 * groupe ne reçoit sinon niveau et classe qu'à l'entrée dans le groupe
 * (PartyJoined/PartyMemberJoined). {@code subclass} : null tant qu'aucune n'est
 * choisie.
 */
public record PartyMemberProfileUpdated(UUID characterId, String characterName, int level,
        CharacterClass characterClass, Subclass subclass) implements OutputJsonMessage {
}
