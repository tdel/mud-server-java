package fr.idev.mudserver.network.message.ingame;

import java.util.*;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.actor.AbstractCharacter;
import fr.idev.mudserver.domain.actor.AbstractNpc;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.MonsterInstance;
import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.world.ZoneInstance;
import fr.idev.mudserver.game.engine.MovementEngine;
import fr.idev.mudserver.network.server.tcpjson.TcpJsonOutput;

public record ViewAround(AbstractCharacter character) implements OutputJsonMessage {

    public static final int VIEWPORT_RADIUS = 5;

    public record CellView(int q, int r, String kind) {
    }

    public record PortalView(String direction, String targetZoneName) {
    }

    public record Payload(String zoneName, String zoneDescription, List<CellView> cells, List<PortalView> portals,
            List<String> charactersNearby, List<String> monstersNearby, List<String> npcsNearby) {
    }

    @Override
    public void toJson(TcpJsonOutput output) {
        ZoneInstance zone = character.getCurrentZone();
        List<AbstractCharacter> nearby = zone.occupantsWithin(character.getPosition(), VIEWPORT_RADIUS);

        List<PortalView> portals = zone.getPortals().stream()
                .map(portal -> new PortalView(portal.direction().toString(), portal.targetZone().getName())).toList();
        List<String> characterNames = nearby.stream().filter(CharacterInstance.class::isInstance)
                .filter(other -> !other.getId().equals(character.getId())).map(AbstractCharacter::getName).toList();
        List<String> monsterNames = nearby.stream().filter(MonsterInstance.class::isInstance)
                .map(AbstractCharacter::getName).toList();
        List<String> npcNames = nearby.stream().filter(AbstractNpc.class::isInstance).map(AbstractCharacter::getName)
                .toList();

        output.write("ViewAround", new Payload(zone.getName(), zone.getDescription(), renderCells(zone, character),
                portals, characterNames, monsterNames, npcNames), false);
    }

    private List<CellView> renderCells(ZoneInstance zone, AbstractCharacter viewer) {
        HexCoordinate center = viewer.getPosition();
        List<HexCoordinate> path = remainingPath(viewer);
        Set<HexCoordinate> pathCells = new HashSet<>(path);
        HexCoordinate destination = path.isEmpty() ? null : path.getLast();
        List<CellView> cells = new ArrayList<>();

        for (int r = center.r() - VIEWPORT_RADIUS; r <= center.r() + VIEWPORT_RADIUS; r++) {
            int dr = r - center.r();
            int dqMin = Math.max(-VIEWPORT_RADIUS, -dr - VIEWPORT_RADIUS);
            int dqMax = Math.min(VIEWPORT_RADIUS, -dr + VIEWPORT_RADIUS);
            for (int dq = dqMin; dq <= dqMax; dq++) {
                HexCoordinate cell = new HexCoordinate(center.q() + dq, r);
                cells.add(new CellView(cell.q(), cell.r(), kindFor(zone, viewer, cell, pathCells, destination)));
            }
        }
        return cells;
    }

    private String kindFor(ZoneInstance zone, AbstractCharacter viewer, HexCoordinate cell,
            Set<HexCoordinate> pathCells, HexCoordinate destination) {
        if (cell.equals(viewer.getPosition())) {
            return "self";
        }
        if (!zone.containsCell(cell)) {
            return "outOfBounds";
        }
        if (!zone.isWalkable(cell)) {
            return "blocked";
        }

        Optional<AbstractCharacter> occupant = zone.occupantAt(cell);
        if (occupant.isPresent()) {
            return switch (occupant.get()) {
                case CharacterInstance ignored -> "player";
                case MonsterInstance ignored -> "monster";
                case AbstractNpc ignored -> "npc";
                default -> throw new IllegalStateException("Type d'occupant inattendu : " + occupant.get().getClass());
            };
        }

        if (cell.equals(destination)) {
            return zone.findPortalAt(cell).isPresent() ? "portalDestination" : "destination";
        }
        if (pathCells.contains(cell)) {
            return "path";
        }
        if (zone.findPortalAt(cell).isPresent()) {
            return "portal";
        }
        return "floor";
    }

    private List<HexCoordinate> remainingPath(AbstractCharacter viewer) {
        MovementEngine.ActiveMovement movement = viewer.activeMovement;
        return movement == null ? List.of() : movement.remainingPath();
    }
}
