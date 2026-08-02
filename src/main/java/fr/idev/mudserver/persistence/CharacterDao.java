package fr.idev.mudserver.persistence;

import static fr.idev.mudserver.persistence.jooq.Tables.CHARACTER;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.Character;
import fr.idev.mudserver.domain.Race;
import fr.idev.mudserver.persistence.jooq.tables.records.CharacterRecord;

@Repository
public class CharacterDao {

    private final DSLContext dsl;

    public CharacterDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insert(Character character) {
        dsl.insertInto(CHARACTER, CHARACTER.ID, CHARACTER.ACCOUNT_ID, CHARACTER.NAME, CHARACTER.CURRENT_ROOM_ID,
                CHARACTER.RACE, CHARACTER.CURRENT_HEALTH, CHARACTER.MAX_HEALTH, CHARACTER.CURRENT_MANA,
                CHARACTER.MAX_MANA, CHARACTER.STRENGTH, CHARACTER.DEXTERITY, CHARACTER.CONSTITUTION,
                CHARACTER.INTELLIGENCE, CHARACTER.WISDOM, CHARACTER.CHARISMA)
                .values(character.getId(), character.getAccountId(), character.getName(), character.getCurrentRoomId(),
                        character.getRace().name(), character.getCurrentHealth(), character.getMaxHealth(),
                        character.getCurrentMana(), character.getMaxMana(), character.getStrength(),
                        character.getDexterity(), character.getConstitution(), character.getIntelligence(),
                        character.getWisdom(), character.getCharisma())
                .execute();
    }

    public Optional<Character> findById(UUID id) {
        return dsl.selectFrom(CHARACTER).where(CHARACTER.ID.eq(id)).fetchOptional(CharacterDao::toDomain);
    }

    public List<Character> findByAccountId(UUID accountId) {
        return dsl.selectFrom(CHARACTER).where(CHARACTER.ACCOUNT_ID.eq(accountId)).fetch(CharacterDao::toDomain);
    }

    public Optional<Character> findByAccountIdAndName(UUID accountId, String name) {
        return dsl.selectFrom(CHARACTER).where(CHARACTER.ACCOUNT_ID.eq(accountId)).and(CHARACTER.NAME.eq(name))
                .fetchOptional(CharacterDao::toDomain);
    }

    public void updateCurrentRoom(UUID characterId, UUID roomId) {
        dsl.update(CHARACTER).set(CHARACTER.CURRENT_ROOM_ID, roomId).where(CHARACTER.ID.eq(characterId)).execute();
    }

    public void deleteById(UUID characterId) {
        dsl.deleteFrom(CHARACTER).where(CHARACTER.ID.eq(characterId)).execute();
    }

    private static Character toDomain(CharacterRecord record) {
        return new Character(record.getId(), record.getAccountId(), record.getName(), record.getCurrentRoomId(),
                Race.valueOf(record.getRace()), record.getCurrentHealth(), record.getMaxHealth(),
                record.getCurrentMana(), record.getMaxMana(), record.getStrength(), record.getDexterity(),
                record.getConstitution(), record.getIntelligence(), record.getWisdom(), record.getCharisma());
    }
}
