package fr.idev.mudserver.persistence;

import static fr.idev.mudserver.persistence.jooq.Tables.ROOM_EXIT;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import fr.idev.mudserver.domain.RoomExit;
import fr.idev.mudserver.persistence.jooq.tables.records.RoomExitRecord;

@Repository
public class RoomExitDao {

    private final DSLContext dsl;

    public RoomExitDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insert(RoomExit exit) {
        dsl.insertInto(ROOM_EXIT, ROOM_EXIT.ID, ROOM_EXIT.DIRECTION, ROOM_EXIT.SOURCE_ROOM_ID, ROOM_EXIT.TARGET_ROOM_ID)
                .values(exit.getId(), exit.getDirection(), exit.getSourceRoomId(), exit.getTargetRoomId()).execute();
    }

    public List<RoomExit> findBySourceRoomId(UUID sourceRoomId) {
        return dsl.selectFrom(ROOM_EXIT).where(ROOM_EXIT.SOURCE_ROOM_ID.eq(sourceRoomId)).fetch(RoomExitDao::toDomain);
    }

    public Optional<RoomExit> findBySourceRoomIdAndDirection(UUID sourceRoomId, String direction) {
        return dsl.selectFrom(ROOM_EXIT).where(ROOM_EXIT.SOURCE_ROOM_ID.eq(sourceRoomId))
                .and(ROOM_EXIT.DIRECTION.eq(direction)).fetchOptional(RoomExitDao::toDomain);
    }

    private static RoomExit toDomain(RoomExitRecord record) {
        return new RoomExit(record.getId(), record.getDirection(), record.getSourceRoomId(), record.getTargetRoomId());
    }
}
