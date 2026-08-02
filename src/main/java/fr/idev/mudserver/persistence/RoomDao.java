package fr.idev.mudserver.persistence;

import static fr.idev.mudserver.persistence.jooq.Tables.ROOM;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.Room;
import fr.idev.mudserver.persistence.jooq.tables.records.RoomRecord;

@Repository
public class RoomDao {

    private final DSLContext dsl;

    public RoomDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insert(Room room) {
        dsl.insertInto(ROOM, ROOM.ID, ROOM.NAME, ROOM.DESCRIPTION, ROOM.IS_STARTING_ROOM)
                .values(room.getId(), room.getName(), room.getDescription(), room.isStartingRoom()).execute();
    }

    public Optional<Room> findById(UUID id) {
        return dsl.selectFrom(ROOM).where(ROOM.ID.eq(id)).fetchOptional(RoomDao::toDomain);
    }

    public Optional<Room> findByName(String name) {
        return dsl.selectFrom(ROOM).where(ROOM.NAME.eq(name)).fetchOptional(RoomDao::toDomain);
    }

    public Optional<Room> findStartingRoom() {
        return dsl.selectFrom(ROOM).where(ROOM.IS_STARTING_ROOM.isTrue()).fetchOptional(RoomDao::toDomain);
    }

    public List<Room> findAll() {
        return dsl.selectFrom(ROOM).fetch(RoomDao::toDomain);
    }

    /**
     * Ne marque rien de plus ; l'appelant doit avoir appelé
     * {@link #clearStartingRoom()} avant si besoin.
     */
    public void markAsStartingRoom(UUID roomId) {
        dsl.update(ROOM).set(ROOM.IS_STARTING_ROOM, true).where(ROOM.ID.eq(roomId)).execute();
    }

    public void clearStartingRoom() {
        dsl.update(ROOM).set(ROOM.IS_STARTING_ROOM, (Boolean) null).where(ROOM.IS_STARTING_ROOM.isTrue()).execute();
    }

    private static Room toDomain(RoomRecord record) {
        return new Room(record.getId(), record.getName(), record.getDescription(), record.getIsStartingRoom());
    }
}
