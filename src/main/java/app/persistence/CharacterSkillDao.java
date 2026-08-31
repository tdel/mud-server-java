package app.persistence;

import static app.persistence.jooq.Tables.CHARACTER_SKILL;

import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class CharacterSkillDao {

    private final DSLContext dsl;

    public CharacterSkillDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<UUID> findSkillIdsByCharacter(UUID characterId) {
        return dsl.select(CHARACTER_SKILL.SKILL_ID).from(CHARACTER_SKILL)
                .where(CHARACTER_SKILL.CHARACTER_ID.eq(characterId)).fetch(CHARACTER_SKILL.SKILL_ID);
    }

    public void insert(UUID characterId, UUID skillId) {
        dsl.insertInto(CHARACTER_SKILL, CHARACTER_SKILL.CHARACTER_ID, CHARACTER_SKILL.SKILL_ID)
                .values(characterId, skillId).execute();
    }

    public void deleteByCharacterAndSkill(UUID characterId, UUID skillId) {
        dsl.deleteFrom(CHARACTER_SKILL).where(CHARACTER_SKILL.CHARACTER_ID.eq(characterId))
                .and(CHARACTER_SKILL.SKILL_ID.eq(skillId)).execute();
    }
}
