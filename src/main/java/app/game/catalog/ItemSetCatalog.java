package app.game.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import app.domain.actor.ModifiedStat;
import app.domain.item.ItemSet;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@Service
public class ItemSetCatalog {

    private static final Logger log = LoggerFactory.getLogger(ItemSetCatalog.class);

    private static final String ITEM_SET_RESOURCE = "/data/item_sets.xml";

    private final Map<String, ItemSet> sets = new ConcurrentHashMap<>();

    private final XmlMapper xmlMapper;

    public ItemSetCatalog(XmlMapper xmlMapper) {
        this.xmlMapper = xmlMapper;
    }

    public void warmItemSets() {
        try (InputStream in = getClass().getResourceAsStream(ITEM_SET_RESOURCE)) {
            List<ItemSetDefinition> definitions = xmlMapper.readValue(in, new TypeReference<List<ItemSetDefinition>>() {
            });
            for (ItemSetDefinition definition : definitions) {
                Map<Integer, Map<ModifiedStat, Integer>> bonusByPieceCount = definition.bonusByPieceCount().stream()
                        .collect(Collectors.toMap(PieceBonusDefinition::pieceCount, PieceBonusDefinition::bonuses));
                sets.put(definition.id(), new ItemSet(definition.id(), definition.name(), bonusByPieceCount));
            }
            log.info("item.sets_loaded count={}", sets.size());
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Impossible de charger " + ITEM_SET_RESOURCE, e);
        }
    }

    public ItemSet getById(String setId) {
        ItemSet set = sets.get(setId);
        if (set == null) {
            throw new IllegalStateException(
                    "ItemSet " + setId + " absent du cache — warmItemSets() a-t-il été appelé ?");
        }
        return set;
    }

    private record ItemSetDefinition(String id, String name, List<PieceBonusDefinition> bonusByPieceCount) {
    }

    private record PieceBonusDefinition(@JacksonXmlProperty(isAttribute = true) int pieceCount,
            Map<ModifiedStat, Integer> bonuses) {
    }
}
