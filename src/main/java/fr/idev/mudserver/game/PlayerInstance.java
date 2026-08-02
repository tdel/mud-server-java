package fr.idev.mudserver.game;

import java.util.UUID;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.persistence.CharacterDao;

/**
 * Une session connectée liée à un {@link Character} pour toute sa durée de vie
 * — changer de personnage signifie se déloguer et en sélectionner un autre, ce
 * qui crée une nouvelle instance. Contrairement au PHP (où {@code character()}
 * relit la relation Doctrine à chaque appel), la copie locale de
 * {@link Character} est mutée en place (voir {@link #moveToRoom}) puis
 * persistée explicitement : pas d'identity-map, donc pas de lecture "toujours à
 * jour" implicite.
 */
public class PlayerInstance {

    private final Connection session;
    private final Character character;
    private final CharacterDao characterDao;

    public PlayerInstance(Connection session, Character character, CharacterDao characterDao) {
        this.session = session;
        this.character = character;
        this.characterDao = characterDao;
    }

    public Character character() {
        return character;
    }

    public UUID currentRoomId() {
        return character.getCurrentRoomId();
    }

    public void moveToRoom(UUID roomId) {
        character.setCurrentRoomId(roomId);
        characterDao.updateCurrentRoom(character.getId(), roomId);
    }

    public void send(OutputMessage message) {
        session.send(message);
    }
}
