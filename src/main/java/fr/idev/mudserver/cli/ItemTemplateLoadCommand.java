package fr.idev.mudserver.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.ItemType;
import fr.idev.mudserver.persistence.ItemTemplateDao;

@Component
public class ItemTemplateLoadCommand implements CliCommand {

    private static final String ITEMS_RESOURCE = "/data/items.json";

    private final ItemTemplateDao itemTemplateDao;
    private final ObjectMapper objectMapper;

    public ItemTemplateLoadCommand(ItemTemplateDao itemTemplateDao, ObjectMapper objectMapper) {
        this.itemTemplateDao = itemTemplateDao;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "item-template-load";
    }

    @Override
    public int run(ApplicationArguments args) {
        List<Entry> entries;
        try (InputStream in = getClass().getResourceAsStream(ITEMS_RESOURCE)) {
            if (in == null) {
                System.err.println("Resource not found: " + ITEMS_RESOURCE);
                return 1;
            }
            entries = objectMapper.readValue(in, new TypeReference<List<Entry>>() {
            });
        } catch (IOException | JacksonException e) {
            System.err.println("Failed to read " + ITEMS_RESOURCE + ": " + e.getMessage());
            return 1;
        }

        int created = 0;
        int skipped = 0;

        for (Entry entry : entries) {
            UUID id = UUID.fromString(entry.id());

            if (itemTemplateDao.existsById(id)) {
                skipped++;
                continue;
            }

            ItemTemplate template = new ItemTemplate(
                    id, entry.name(), entry.description(),
                    ItemType.valueOf(entry.type().toUpperCase(Locale.ROOT)), entry.weight()
            );
            itemTemplateDao.insert(template);
            created++;
        }

        System.out.printf("%d item template(s) created, %d already present.%n", created, skipped);
        return 0;
    }

    private record Entry(String id, String name, String description, String type, int weight) {
    }
}
