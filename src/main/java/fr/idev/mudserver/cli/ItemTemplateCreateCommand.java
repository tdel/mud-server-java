package fr.idev.mudserver.cli;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.ItemType;
import fr.idev.mudserver.persistence.ItemTemplateDao;

@Component
public class ItemTemplateCreateCommand implements CliCommand {

    private final ItemTemplateDao itemTemplateDao;

    public ItemTemplateCreateCommand(ItemTemplateDao itemTemplateDao) {
        this.itemTemplateDao = itemTemplateDao;
    }

    @Override
    public String name() {
        return "item-template-create";
    }

    @Override
    public int run(ApplicationArguments args) {
        String name = CliOptions.first(args, "name");
        String description = CliOptions.first(args, "description");
        String typeOption = CliOptions.first(args, "type");
        String weightOption = CliOptions.first(args, "weight");

        if (name == null || description == null || typeOption == null || weightOption == null) {
            System.err.println(
                    "Usage: item-template-create --name=<name> --description=<description> --type=<type> --weight=<weight>");
            System.err.println("Valid types: " + Arrays.toString(ItemType.values()));
            return 1;
        }

        if (itemTemplateDao.findByName(name).isPresent()) {
            System.err.println("An item template named \"" + name + "\" already exists.");
            return 1;
        }

        ItemType type;
        try {
            type = ItemType.valueOf(typeOption.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            System.err
                    .println("Unknown type \"" + typeOption + "\". Valid types: " + Arrays.toString(ItemType.values()));
            return 1;
        }

        int weight;
        try {
            weight = Integer.parseInt(weightOption);
        } catch (NumberFormatException e) {
            System.err.println("Weight must be an integer, got \"" + weightOption + "\".");
            return 1;
        }

        ItemTemplate template = new ItemTemplate(UUID.randomUUID(), name, description, type, weight);
        itemTemplateDao.insert(template);

        System.out.printf("Item template \"%s\" created (id=%s).%n", name, template.id());
        return 0;
    }
}
