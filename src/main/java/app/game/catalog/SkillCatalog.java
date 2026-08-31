package app.game.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import app.domain.ActiveSkill;
import app.domain.SkillEffectType;
import app.domain.SkillElement;
import app.domain.actor.CharacterClass;
import app.domain.actor.ModifiedStat;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class SkillCatalog {

    private static final Logger log = LoggerFactory.getLogger(SkillCatalog.class);

    private static final String SKILL_RESOURCE = "/data/skills/skills.json";

    private final Map<UUID, ActiveSkill> activeSkills = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public SkillCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void warmSkills() {
        try (InputStream in = getClass().getResourceAsStream(SKILL_RESOURCE)) {
            List<SkillDefinition> definitions = objectMapper.readValue(in, new TypeReference<List<SkillDefinition>>() {
            });
            for (SkillDefinition definition : definitions) {
                if (definition.projectile() && definition.projectileSpeed() <= 0) {
                    throw new IllegalStateException("ActiveSkill " + definition.id() + " (" + definition.name()
                            + " tier " + definition.tier()
                            + ") est un projectile mais n'a pas de projectileSpeed positif dans " + SKILL_RESOURCE);
                }
                ActiveSkill activeSkill = new ActiveSkill(definition.id(), definition.name(),
                        definition.tier() == null ? 1 : definition.tier(), definition.description(),
                        definition.requiredLevel(), definition.manaCost(), definition.cooldownSeconds(),
                        definition.castingTimeMs(), definition.range(), definition.effect(), definition.power(),
                        definition.element() == null ? SkillElement.NONE : definition.element(),
                        definition.projectile(), definition.projectileSpeed(), Set.copyOf(definition.classes()),
                        definition.modifiedStat(),
                        definition.durationSeconds() == null ? 0 : definition.durationSeconds());
                if (activeSkills.containsKey(activeSkill.id())) {
                    throw new IllegalStateException("ActiveSkill " + activeSkill.id() + " (" + activeSkill.name()
                            + " tier " + activeSkill.tier() + ") a un id déjà utilisé par "
                            + activeSkills.get(activeSkill.id()).name() + " tier "
                            + activeSkills.get(activeSkill.id()).tier() + " dans " + SKILL_RESOURCE);
                }
                activeSkills.put(activeSkill.id(), activeSkill);
            }
            validateTierFamilies(activeSkills.values());
            log.info("activeSkill.templates_loaded count={}", activeSkills.size());
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + SKILL_RESOURCE, e);
        }
    }

    public ActiveSkill getById(UUID skillId) {
        ActiveSkill activeSkill = activeSkills.get(skillId);
        if (activeSkill == null) {
            throw new IllegalStateException(
                    "ActiveSkill " + skillId + " absent du cache — warmSkills() a-t-il été appelé ?");
        }
        return activeSkill;
    }

    public List<ActiveSkill> skillsLearnableAt(CharacterClass characterClass, int level) {
        return activeSkills.values().stream().filter(
                activeSkill -> activeSkill.requiredLevel() == level && activeSkill.classes().contains(characterClass))
                .toList();
    }

    static void validateTierFamilies(Collection<ActiveSkill> allSkills) {
        Map<String, List<ActiveSkill>> families = allSkills.stream().collect(Collectors.groupingBy(ActiveSkill::name));
        for (Map.Entry<String, List<ActiveSkill>> family : families.entrySet()) {
            List<ActiveSkill> tiers = new ArrayList<>(family.getValue());
            tiers.sort(Comparator.comparingInt(ActiveSkill::tier));
            for (int i = 0; i < tiers.size(); i++) {
                int expectedTier = i + 1;
                if (tiers.get(i).tier() != expectedTier) {
                    throw new IllegalStateException(
                            "Sort '" + family.getKey() + "' a des tiers non contigus dans " + SKILL_RESOURCE + " (tier "
                                    + expectedTier + " attendu, trouvé " + tiers.get(i).tier() + ")");
                }
                if (i > 0 && tiers.get(i).requiredLevel() <= tiers.get(i - 1).requiredLevel()) {
                    throw new IllegalStateException("Sort '" + family.getKey() + "' tier " + tiers.get(i).tier()
                            + " a un requiredLevel=" + tiers.get(i).requiredLevel()
                            + " qui n'est pas strictement supérieur à celui du tier " + tiers.get(i - 1).tier() + " ("
                            + tiers.get(i - 1).requiredLevel() + ") dans " + SKILL_RESOURCE);
                }
            }
        }
    }

    private record SkillDefinition(UUID id, String name, Integer tier, String description, int requiredLevel,
            int manaCost, int cooldownSeconds, int castingTimeMs, int range, SkillEffectType effect, int power,
            SkillElement element, boolean projectile, int projectileSpeed, List<CharacterClass> classes,
            ModifiedStat modifiedStat, Integer durationSeconds) {
    }
}
