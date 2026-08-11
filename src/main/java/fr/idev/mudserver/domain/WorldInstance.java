package fr.idev.mudserver.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Playthrough concret d'un {@link WorldTemplate}, scopé à une party — chaque
 * party qui lance un même {@code WorldTemplate} obtient sa propre
 * {@code WorldInstance}, avec son propre graphe de {@link RoomInstance}s, ses
 * propres monstres/PNJ/items, invisible aux autres instances. Conflate
 * volontairement métadonnées persistées et conteneur runtime, comme
 * {@link RoomInstance} le fait déjà pour {@link RoomTemplate} — pas de split
 * supplémentaire tant qu'aucun second besoin (éviction sous pression mémoire,
 * par exemple) ne le justifie.
 *
 * <p>
 * {@code roomInstances} est vide tant que {@code WorldInstanceService
 * .materialize} n'a pas tourné (voir {@link #isMaterialized()}) — une
 * {@code WorldInstance} peut exister en DB (créée par une party, puis tout le
 * monde déconnecté) sans être résidente en mémoire, et n'est matérialisée à
 * nouveau qu'à la demande. Keyé par id de {@link RoomTemplate} (pas par id de
 * {@link RoomInstance} lui-même) pour que
 * {@link #roomInstanceForTemplate(UUID)} — la résolution "quelle room du monde"
 * utilisée à la reconnexion d'un personnage — reste une simple consultation de
 * map.
 */
public class WorldInstance {

    /**
     * Id fixe (pas généré à l'exécution) de la {@code WorldInstance} par défaut
     * créée pour ne rien perdre des personnages déjà existants au moment de
     * l'introduction des Worlds — doit rester synchronisé avec le littéral de
     * {@code V8__add_character_world_instance.sql} et avec la migration Java
     * {@code V9__RecomputeDefaultInstanceItemRoomIds}. Référencé par
     * {@code CharacterDao.insert} (repli quand un {@code GamePlayer} construit à la
     * main n'a jamais reçu d'autre instance explicite) et par
     * {@code WorldInstanceService} (chargement/matérialisation au démarrage tant
     * qu'aucun Lobby ne permet encore de choisir un autre monde).
     */
    public static final UUID DEFAULT_ID = UUID.fromString("a8e98a8e-73c1-43dd-b36e-a2f67f00ff48");

    private final UUID id;
    private final UUID worldTemplateId;
    private final Instant createdAt;
    private final UUID partyLeaderAccountId;
    private final Set<UUID> memberAccountIds;

    private Map<UUID, RoomInstance> roomInstances = Map.of();

    public WorldInstance(UUID id, UUID worldTemplateId, Instant createdAt, UUID partyLeaderAccountId,
            Set<UUID> memberAccountIds) {
        this.id = id;
        this.worldTemplateId = worldTemplateId;
        this.createdAt = createdAt;
        this.partyLeaderAccountId = partyLeaderAccountId;
        this.memberAccountIds = Set.copyOf(memberAccountIds);
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorldTemplateId() {
        return worldTemplateId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Optional<UUID> getPartyLeaderAccountId() {
        return Optional.ofNullable(partyLeaderAccountId);
    }

    public Set<UUID> getMemberAccountIds() {
        return memberAccountIds;
    }

    public void setRoomInstances(Map<UUID, RoomInstance> roomInstances) {
        this.roomInstances = Map.copyOf(roomInstances);
    }

    public boolean isMaterialized() {
        return !roomInstances.isEmpty();
    }

    public Collection<RoomInstance> roomInstances() {
        return roomInstances.values();
    }

    public Optional<RoomInstance> roomInstanceForTemplate(UUID roomTemplateId) {
        return Optional.ofNullable(roomInstances.get(roomTemplateId));
    }

    public Optional<RoomInstance> startingRoomInstance() {
        return roomInstances.values().stream().filter(room -> Boolean.TRUE.equals(room.isStartingRoom())).findFirst();
    }

    @Override
    public String toString() {
        return "WorldInstance[id=" + id + ", worldTemplateId=" + worldTemplateId + ", createdAt=" + createdAt
                + ", partyLeaderAccountId=" + partyLeaderAccountId + ", members=" + memberAccountIds.size() + "]";
    }
}
