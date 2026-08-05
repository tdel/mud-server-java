package fr.idev.mudserver.persistence;

import static fr.idev.mudserver.persistence.jooq.Tables.CHARACTER;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.GamePlayer;
import fr.idev.mudserver.domain.actor.CharacterClass;
import fr.idev.mudserver.domain.actor.Gender;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.persistence.jooq.tables.records.CharacterRecord;

@Repository
public class CharacterDao {

    private final DSLContext dsl;

    public CharacterDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insert(GamePlayer character) {
        dsl.insertInto(CHARACTER, CHARACTER.ID, CHARACTER.ACCOUNT_ID, CHARACTER.NAME, CHARACTER.CURRENT_ROOM_ID,
                CHARACTER.GENDER, CHARACTER.RACE, CHARACTER.CHARACTER_CLASS, CHARACTER.LEVEL, CHARACTER.CURRENT_HEALTH,
                CHARACTER.MAX_HEALTH, CHARACTER.STRENGTH, CHARACTER.DEXTERITY, CHARACTER.CONSTITUTION,
                CHARACTER.INTELLIGENCE, CHARACTER.WISDOM, CHARACTER.CHARISMA)
                .values(character.getId(), character.getAccountId(), character.getName(), character.getCurrentRoomId(),
                        character.getGender().name(), character.getRace().name(), character.getCharacterClass().name(),
                        character.getLevel(), character.getCurrentHealth(), character.getMaxHealth(),
                        character.getAttribute(Attribute.STRENGTH), character.getAttribute(Attribute.DEXTERITY),
                        character.getAttribute(Attribute.CONSTITUTION), character.getAttribute(Attribute.INTELLIGENCE),
                        character.getAttribute(Attribute.WISDOM), character.getAttribute(Attribute.CHARISMA))
                .execute();
    }

    public Optional<GamePlayer> findById(UUID id) {
        return dsl.selectFrom(CHARACTER).where(CHARACTER.ID.eq(id)).fetchOptional(CharacterDao::toDomain);
    }

    public List<GamePlayer> findByAccountId(UUID accountId) {
        return dsl.selectFrom(CHARACTER).where(CHARACTER.ACCOUNT_ID.eq(accountId)).fetch(CharacterDao::toDomain);
    }

    public Optional<GamePlayer> findByAccountIdAndName(UUID accountId, String name) {
        return dsl.selectFrom(CHARACTER).where(CHARACTER.ACCOUNT_ID.eq(accountId)).and(CHARACTER.NAME.eq(name))
                .fetchOptional(CharacterDao::toDomain);
    }

    public void updateCurrentRoom(UUID characterId, UUID roomId) {
        dsl.update(CHARACTER).set(CHARACTER.CURRENT_ROOM_ID, roomId).where(CHARACTER.ID.eq(characterId)).execute();
    }

    /**
     * Ne persiste que les champs qui évoluent réellement en jeu (position, santé) ;
     * race/stats/nom restent figés à la création, pas besoin de les réécrire.
     */
    public void update(GamePlayer character) {
        dsl.update(CHARACTER).set(CHARACTER.CURRENT_ROOM_ID, character.getCurrentRoomId())
                .set(CHARACTER.CURRENT_HEALTH, character.getCurrentHealth()).where(CHARACTER.ID.eq(character.getId()))
                .execute();
    }

    public void deleteById(UUID characterId) {
        dsl.deleteFrom(CHARACTER).where(CHARACTER.ID.eq(characterId)).execute();
    }

    private static GamePlayer toDomain(CharacterRecord record) {
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        attributes.put(Attribute.STRENGTH, record.getStrength());
        attributes.put(Attribute.DEXTERITY, record.getDexterity());
        attributes.put(Attribute.CONSTITUTION, record.getConstitution());
        attributes.put(Attribute.INTELLIGENCE, record.getIntelligence());
        attributes.put(Attribute.WISDOM, record.getWisdom());
        attributes.put(Attribute.CHARISMA, record.getCharisma());

        return new GamePlayer(record.getId(), record.getAccountId(), record.getName(), record.getCurrentRoomId(),
                Gender.valueOf(record.getGender()), Race.valueOf(record.getRace()),
                CharacterClass.valueOf(record.getCharacterClass()), record.getLevel(), record.getCurrentHealth(),
                record.getMaxHealth(), attributes);
    }
}
