package app.game.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@Service
public class LevelCatalog {

    private static final String LEVELS_RESOURCE = "/data/levels.xml";

    private final Map<Integer, Integer> xpRequiredByLevel = new ConcurrentHashMap<>();
    private final XmlMapper xmlMapper;

    public LevelCatalog(XmlMapper xmlMapper) {
        this.xmlMapper = xmlMapper;
    }

    public void warmXpThresholds() {
        try (InputStream in = getClass().getResourceAsStream(LEVELS_RESOURCE)) {
            List<LevelDefinition> definitions = xmlMapper.readValue(in, new TypeReference<List<LevelDefinition>>() {
            });
            for (LevelDefinition definition : definitions) {
                xpRequiredByLevel.put(definition.id(), definition.xp());
            }
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + LEVELS_RESOURCE, e);
        }
    }

    public int xpRequiredForLevel(int level) {
        Integer xp = xpRequiredByLevel.get(level);
        if (xp == null) {
            throw new IllegalStateException(
                    "XP requis pour le niveau " + level + " absent du cache — warmXpThresholds() a-t-il été appelé ?");
        }
        return xp;
    }

    public int maxLevel() {
        return xpRequiredByLevel.size();
    }

    private record LevelDefinition(@JacksonXmlProperty(isAttribute = true) int id,
            @JacksonXmlProperty(isAttribute = true) int xp) {
    }
}
