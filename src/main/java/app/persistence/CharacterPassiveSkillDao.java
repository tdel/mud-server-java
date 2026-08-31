package app.persistence;

import static app.persistence.jooq.Tables.CHARACTER_PASSIVE_SKILL;

import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class CharacterPassiveSkillDao {

    private final DSLContext dsl;

    public CharacterPassiveSkillDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<UUID> findPassiveSkillIdsByCharacter(UUID characterId) {
        return dsl.select(CHARACTER_PASSIVE_SKILL.PASSIVE_SKILL_ID).from(CHARACTER_PASSIVE_SKILL)
                .where(CHARACTER_PASSIVE_SKILL.CHARACTER_ID.eq(characterId))
                .fetch(CHARACTER_PASSIVE_SKILL.PASSIVE_SKILL_ID);
    }

    public void insert(UUID characterId, UUID passiveSkillId) {
        dsl.insertInto(CHARACTER_PASSIVE_SKILL, CHARACTER_PASSIVE_SKILL.CHARACTER_ID,
                CHARACTER_PASSIVE_SKILL.PASSIVE_SKILL_ID).values(characterId, passiveSkillId).execute();
    }
}
