package app.game.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import app.domain.actor.ModifiedStat;
import app.domain.item.ItemSet;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ItemSetCatalog {

    private static final Logger log = LoggerFactory.getLogger(ItemSetCatalog.class);

    private static final String ITEM_SET_RESOURCE = "/data/item_sets.json";

    private final Map<String, ItemSet> sets = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public ItemSetCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void warmItemSets() {
        try (InputStream in = getClass().getResourceAsStream(ITEM_SET_RESOURCE)) {
            List<ItemSetDefinition> definitions = objectMapper.readValue(in,
                    new TypeReference<List<ItemSetDefinition>>() {
                    });
            for (ItemSetDefinition definition : definitions) {
                sets.put(definition.id(),
                        new ItemSet(definition.id(), definition.name(), definition.bonusByPieceCount()));
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

    private record ItemSetDefinition(String id, String name,
            Map<Integer, Map<ModifiedStat, Integer>> bonusByPieceCount) {
    }
}
