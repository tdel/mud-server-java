package fr.idev.mudserver.cli;

import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Item;
import fr.idev.mudserver.domain.ItemTemplate;
import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.persistence.ItemDao;
import fr.idev.mudserver.persistence.ItemTemplateDao;
import fr.idev.mudserver.persistence.RoomDao;

@Component
public class ItemSpawnCommand implements CliCommand {

    private final ItemDao itemDao;
    private final ItemTemplateDao itemTemplateDao;
    private final RoomDao roomDao;

    public ItemSpawnCommand(ItemDao itemDao, ItemTemplateDao itemTemplateDao, RoomDao roomDao) {
        this.itemDao = itemDao;
        this.itemTemplateDao = itemTemplateDao;
        this.roomDao = roomDao;
    }

    @Override
    public String name() {
        return "item-spawn";
    }

    @Override
    public int run(ApplicationArguments args) {
        String templateName = CliOptions.first(args, "template");
        String roomName = CliOptions.first(args, "room");

        if (templateName == null || roomName == null) {
            System.err.println("Usage: item-spawn --template=<template name> --room=<room name>");
            return 1;
        }

        Optional<ItemTemplate> template = itemTemplateDao.findByName(templateName);
        if (template.isEmpty()) {
            System.err.println("No item template named \"" + templateName + "\".");
            return 1;
        }

        Optional<Room> room = roomDao.findByName(roomName);
        if (room.isEmpty()) {
            System.err.println("No room named \"" + roomName + "\".");
            return 1;
        }

        Item item = new Item(UUID.randomUUID(), template.get().id(), room.get().id(), null, null);
        itemDao.insert(item);

        System.out.printf("Spawned \"%s\" (id=%s) in room \"%s\".%n", template.get().name(), item.id(),
                room.get().name());
        return 0;
    }
}
