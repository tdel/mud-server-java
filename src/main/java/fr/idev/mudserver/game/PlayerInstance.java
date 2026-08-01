package fr.idev.mudserver.game;

import java.util.UUID;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.network.OutputMessage;
import fr.idev.mudserver.network.Session;
import fr.idev.mudserver.persistence.CharacterDao;

/**
 * Une session connectée liée à un {@link Character} pour toute sa durée de vie
 * — changer de personnage signifie se déloguer et en sélectionner un autre, ce
 * qui crée une nouvelle instance. Contrairement au PHP (où {@code character()}
 * relit la relation Doctrine à chaque appel), le record {@link Character} est
 * copié localement et remplacé explicitement à chaque mutation (voir
 * {@link #moveToRoom}) : pas d'identity-map, donc pas de lecture "toujours à
 * jour" implicite.
 */
public class PlayerInstance {

    private final Session session;
    private Character character;

    public PlayerInstance(Session session, Character character) {
        this.session = session;
        this.character = character;
        session.attachPlayer(this);
    }

    public Character character() {
        return character;
    }

    public UUID currentRoomId() {
        return character.currentRoomId();
    }

    public void moveToRoom(UUID roomId, CharacterDao characterDao) {
        characterDao.updateCurrentRoom(character.id(), roomId);
        character = new Character(character.id(), character.accountId(), character.name(), roomId, character.race(),
                character.currentHealth(), character.maxHealth(), character.currentMana(), character.maxMana(),
                character.strength(), character.dexterity(), character.constitution(), character.intelligence(),
                character.wisdom(), character.charisma());
    }

    public void send(OutputMessage message) {
        session.send(message);
    }
}
