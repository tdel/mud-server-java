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

    public List<CharacterSkillRow> findByCharacter(UUID characterId) {
        return dsl.select(CHARACTER_SKILL.SKILL_ID, CHARACTER_SKILL.LEVEL).from(CHARACTER_SKILL)
                .where(CHARACTER_SKILL.CHARACTER_ID.eq(characterId))
                .fetch(record -> new CharacterSkillRow(record.get(CHARACTER_SKILL.SKILL_ID),
                        record.get(CHARACTER_SKILL.LEVEL)));
    }

    public void insert(UUID characterId, UUID skillId, int level) {
        dsl.insertInto(CHARACTER_SKILL, CHARACTER_SKILL.CHARACTER_ID, CHARACTER_SKILL.SKILL_ID, CHARACTER_SKILL.LEVEL)
                .values(characterId, skillId, level).execute();
    }

    public void updateLevel(UUID characterId, UUID skillId, int level) {
        dsl.update(CHARACTER_SKILL).set(CHARACTER_SKILL.LEVEL, level)
                .where(CHARACTER_SKILL.CHARACTER_ID.eq(characterId)).and(CHARACTER_SKILL.SKILL_ID.eq(skillId))
                .execute();
    }

    public void deleteByCharacterAndSkill(UUID characterId, UUID skillId) {
        dsl.deleteFrom(CHARACTER_SKILL).where(CHARACTER_SKILL.CHARACTER_ID.eq(characterId))
                .and(CHARACTER_SKILL.SKILL_ID.eq(skillId)).execute();
    }

    public record CharacterSkillRow(UUID skillId, int level) {
    }
}
