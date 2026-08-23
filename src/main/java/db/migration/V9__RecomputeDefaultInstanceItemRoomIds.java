package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import fr.idev.mudserver.domain.world.WorldInstance;
import fr.idev.mudserver.domain.world.ZoneInstance;

public class V9__RecomputeDefaultInstanceItemRoomIds extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        List<Object[]> updates = new ArrayList<>();
        try (PreparedStatement select = connection
                .prepareStatement("SELECT id, room_id FROM item WHERE room_id IS NOT NULL");
                ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                UUID itemId = UUID.fromString(rs.getString("id"));
                UUID oldRoomId = UUID.fromString(rs.getString("room_id"));
                UUID newRoomId = ZoneInstance.deterministicId(WorldInstance.DEFAULT_ID, oldRoomId);
                updates.add(new Object[]{itemId, newRoomId});
            }
        }

        try (PreparedStatement update = connection.prepareStatement("UPDATE item SET room_id = ? WHERE id = ?")) {
            for (Object[] row : updates) {
                update.setString(1, row[1].toString());
                update.setString(2, row[0].toString());
                update.addBatch();
            }
            update.executeBatch();
        }
    }
}
