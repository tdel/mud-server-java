package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.network.OutputJsonMessage;

/**
 * Diffusé aux joueurs qui connaissent le personnage (sa KnownList) quand il
 * équipe/retire un objet : liste complète de ce qu'il porte désormais, pour que
 * leur client le rhabille sans attendre un nouvel EntityAppeared (qui porte le
 * même champ {@code equipment}, voir {@link EntityView}).
 */
public record CharacterAppearanceChanged(UUID characterId, String characterName,
        List<EquipmentView> equipment) implements OutputJsonMessage {
}
