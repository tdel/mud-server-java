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

import app.domain.Spell;
import app.domain.SpellEffectType;
import app.domain.actor.CharacterClass;
import app.domain.actor.ModifiedStat;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class SpellCatalog {

    private static final Logger log = LoggerFactory.getLogger(SpellCatalog.class);

    private static final String SPELL_RESOURCE = "/data/spells.json";

    private final Map<UUID, Spell> spells = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public SpellCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void warmSpells() {
        try (InputStream in = getClass().getResourceAsStream(SPELL_RESOURCE)) {
            List<SpellDefinition> definitions = objectMapper.readValue(in, new TypeReference<List<SpellDefinition>>() {
            });
            for (SpellDefinition definition : definitions) {
                Spell spell = new Spell(definition.id(), definition.name(),
                        definition.tier() == null ? 1 : definition.tier(), definition.description(),
                        definition.requiredLevel(), definition.manaCost(), definition.cooldownSeconds(),
                        definition.range(), definition.effect(), definition.effectDice(),
                        Set.copyOf(definition.classes()), definition.modifiedStat(),
                        definition.durationSeconds() == null ? 0 : definition.durationSeconds());
                if (spells.containsKey(spell.id())) {
                    throw new IllegalStateException("Spell " + spell.id() + " (" + spell.name() + " tier "
                            + spell.tier() + ") a un id déjà utilisé par " + spells.get(spell.id()).name() + " tier "
                            + spells.get(spell.id()).tier() + " dans " + SPELL_RESOURCE);
                }
                spells.put(spell.id(), spell);
            }
            validateTierFamilies(spells.values());
            log.info("spell.templates_loaded count={}", spells.size());
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + SPELL_RESOURCE, e);
        }
    }

    public Spell getById(UUID spellId) {
        Spell spell = spells.get(spellId);
        if (spell == null) {
            throw new IllegalStateException("Spell " + spellId + " absent du cache — warmSpells() a-t-il été appelé ?");
        }
        return spell;
    }

    public Collection<Spell> allSpells() {
        return List.copyOf(spells.values());
    }

    public List<Spell> spellsLearnableAt(CharacterClass characterClass, int level) {
        return spells.values().stream()
                .filter(spell -> spell.requiredLevel() == level && spell.classes().contains(characterClass)).toList();
    }

    static void validateTierFamilies(Collection<Spell> allSpells) {
        Map<String, List<Spell>> families = allSpells.stream().collect(Collectors.groupingBy(Spell::name));
        for (Map.Entry<String, List<Spell>> family : families.entrySet()) {
            List<Spell> tiers = new ArrayList<>(family.getValue());
            tiers.sort(Comparator.comparingInt(Spell::tier));
            for (int i = 0; i < tiers.size(); i++) {
                int expectedTier = i + 1;
                if (tiers.get(i).tier() != expectedTier) {
                    throw new IllegalStateException(
                            "Sort '" + family.getKey() + "' a des tiers non contigus dans " + SPELL_RESOURCE + " (tier "
                                    + expectedTier + " attendu, trouvé " + tiers.get(i).tier() + ")");
                }
                if (i > 0 && tiers.get(i).requiredLevel() <= tiers.get(i - 1).requiredLevel()) {
                    throw new IllegalStateException("Sort '" + family.getKey() + "' tier " + tiers.get(i).tier()
                            + " a un requiredLevel=" + tiers.get(i).requiredLevel()
                            + " qui n'est pas strictement supérieur à celui du tier " + tiers.get(i - 1).tier() + " ("
                            + tiers.get(i - 1).requiredLevel() + ") dans " + SPELL_RESOURCE);
                }
            }
        }
    }

    private record SpellDefinition(UUID id, String name, Integer tier, String description, int requiredLevel,
            int manaCost, int cooldownSeconds, int range, SpellEffectType effect, String effectDice,
            List<CharacterClass> classes, ModifiedStat modifiedStat, Integer durationSeconds) {
    }
}
