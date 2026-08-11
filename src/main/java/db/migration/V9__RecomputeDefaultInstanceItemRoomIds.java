package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import fr.idev.mudserver.domain.RoomInstance;
import fr.idev.mudserver.domain.WorldInstance;

/**
 * {@code item.room_id} référençait jusqu'ici directement l'id d'une room (ce
 * qui, avant l'introduction des Worlds, était aussi l'id de son
 * {@code RoomTemplate} — les deux se confondaient). Depuis
 * {@code V7__add_world_instance.sql}/{@code V8__add_character_world_instance.sql},
 * {@code item.room_id} doit désigner l'id déterministe d'une
 * {@link RoomInstance} (voir {@link RoomInstance#deterministicId}), calculé à
 * partir de (WorldInstance, RoomTemplate) — les valeurs déjà en base pointent
 * encore vers l'ancien id de RoomTemplate et doivent être recalculées pour la
 * {@link WorldInstance#DEFAULT_ID} à laquelle tout personnage/item existant a
 * été rattaché par {@code V8}.
 *
 * <p>
 * Migration Java plutôt que SQL : {@link UUID#nameUUIDFromBytes} (MD5, UUID v3
 * RFC 4122) n'a pas d'équivalent direct en SQL Postgres pur — le reproduire
 * fidèlement en PL/pgSQL serait plus fragile qu'appeler la même méthode Java
 * des deux côtés (ici et {@code WorldInstanceService.materialize}).
 */
public class V9__RecomputeDefaultInstanceItemRoomIds extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        List<Object[]> updates = new ArrayList<>();
        try (PreparedStatement select = connection
                .prepareStatement("SELECT id, room_id FROM item WHERE room_id IS NOT NULL");
                ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                UUID itemId = (UUID) rs.getObject("id");
                UUID oldRoomId = (UUID) rs.getObject("room_id");
                UUID newRoomId = RoomInstance.deterministicId(WorldInstance.DEFAULT_ID, oldRoomId);
                updates.add(new Object[]{itemId, newRoomId});
            }
        }

        try (PreparedStatement update = connection.prepareStatement("UPDATE item SET room_id = ? WHERE id = ?")) {
            for (Object[] row : updates) {
                update.setObject(1, row[1]);
                update.setObject(2, row[0]);
                update.addBatch();
            }
            update.executeBatch();
        }
    }
}
