package fr.idev.mudserver.cli;

import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.persistence.RoomDao;

@Component
public class RoomCreateCommand implements CliCommand {

    private final RoomDao roomDao;

    public RoomCreateCommand(RoomDao roomDao) {
        this.roomDao = roomDao;
    }

    @Override
    public String name() {
        return "room-create";
    }

    @Override
    public int run(ApplicationArguments args) {
        String name = CliOptions.first(args, "name");
        String description = CliOptions.first(args, "description");

        if (name == null || description == null) {
            System.err.println("Usage: room-create --name=<name> --description=<description> [--starting]");
            return 1;
        }

        boolean markStarting = args.containsOption("starting");
        Optional<Room> existingStarting = roomDao.findStartingRoom();

        if (markStarting && existingStarting.isPresent()) {
            roomDao.clearStartingRoom();
        }

        Room room = new Room(UUID.randomUUID(), name, description, markStarting ? true : null);
        roomDao.insert(room);

        System.out.printf(
                "Room \"%s\" created (id=%s)%s.%n",
                name, room.id(), markStarting ? ", marked as the starting room" : ""
        );
        return 0;
    }
}
