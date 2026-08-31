package app.game.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import app.domain.PassiveSkill;
import app.domain.item.ItemGrade;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class PassiveSkillCatalog {

    private static final Logger log = LoggerFactory.getLogger(PassiveSkillCatalog.class);

    private static final String PASSIVE_SKILL_RESOURCE = "/data/skills/passives.json";

    private final Map<UUID, PassiveSkill> passiveSkills = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public PassiveSkillCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void warmPassiveSkills() {
        try (InputStream in = getClass().getResourceAsStream(PASSIVE_SKILL_RESOURCE)) {
            List<PassiveSkillDefinition> definitions = objectMapper.readValue(in,
                    new TypeReference<List<PassiveSkillDefinition>>() {
                    });
            for (PassiveSkillDefinition definition : definitions) {
                PassiveSkill passiveSkill = new PassiveSkill(definition.id(), definition.name(),
                        definition.description(), definition.requiredLevel(), definition.grantsGrade());
                if (passiveSkills.containsKey(passiveSkill.id())) {
                    throw new IllegalStateException("Compétence passive " + passiveSkill.id() + " ("
                            + passiveSkill.name() + ") a un id déjà utilisé par "
                            + passiveSkills.get(passiveSkill.id()).name() + " dans " + PASSIVE_SKILL_RESOURCE);
                }
                passiveSkills.put(passiveSkill.id(), passiveSkill);
            }
            log.info("passive_skill.templates_loaded count={}", passiveSkills.size());
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + PASSIVE_SKILL_RESOURCE, e);
        }
    }

    public PassiveSkill getById(UUID passiveSkillId) {
        PassiveSkill passiveSkill = passiveSkills.get(passiveSkillId);
        if (passiveSkill == null) {
            throw new IllegalStateException("Compétence passive " + passiveSkillId
                    + " absente du cache — warmPassiveSkills() a-t-il été appelé ?");
        }
        return passiveSkill;
    }

    private record PassiveSkillDefinition(UUID id, String name, String description, int requiredLevel,
            ItemGrade grantsGrade) {
    }
}
