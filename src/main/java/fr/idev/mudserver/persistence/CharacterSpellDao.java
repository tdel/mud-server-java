package fr.idev.mudserver.persistence;

import static fr.idev.mudserver.persistence.jooq.Tables.CHARACTER_SPELL;

import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class CharacterSpellDao {

    private final DSLContext dsl;

    public CharacterSpellDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<UUID> findSpellIdsByCharacter(UUID characterId) {
        return dsl.select(CHARACTER_SPELL.SPELL_ID).from(CHARACTER_SPELL)
                .where(CHARACTER_SPELL.CHARACTER_ID.eq(characterId)).fetch(CHARACTER_SPELL.SPELL_ID);
    }

    public void insert(UUID characterId, UUID spellId) {
        dsl.insertInto(CHARACTER_SPELL, CHARACTER_SPELL.CHARACTER_ID, CHARACTER_SPELL.SPELL_ID)
                .values(characterId, spellId).execute();
    }
}
