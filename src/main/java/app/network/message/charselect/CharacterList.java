package app.network.message.charselect;

import java.util.List;

import app.network.OutputJsonMessage;
import app.domain.actor.CharacterClass;
import app.domain.actor.Gender;
import app.domain.actor.Race;
import app.network.message.ingame.EquipmentView;

public record CharacterList(List<Entry> characters) implements OutputJsonMessage {

    /**
     * Sexe + équipement porté : de quoi afficher le personnage habillé sur l'écran
     * de sélection, comme en jeu (mêmes champs qu'EntityView).
     */
    public record Entry(String name, Race race, CharacterClass characterClass, int level, Gender gender,
            List<EquipmentView> equipment) {
    }

}
