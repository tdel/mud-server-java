package app.network.message.ingame;

import java.util.List;
import java.util.UUID;

import app.network.OutputJsonMessage;

// Réponse à class-list : parcours de classes du personnage (classPath, de la
// classe de base à la profession actuelle), professions proposées au palier
// suivant (`tier`, 0 si toutes prises) et level requis (null : plus aucun
// changement possible). Les options restent listées sous le level requis pour
// que le joueur puisse les découvrir, mais choose-subclass les refuse.
public record ClassChangeOptions(UUID npcId, String npcName, int characterLevel, String currentClass,
        List<String> classPath, int tier, Integer requiredLevel,
        List<ClassOptionView> options) implements OutputJsonMessage {

}
