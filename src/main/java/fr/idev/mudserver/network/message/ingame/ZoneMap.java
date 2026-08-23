package fr.idev.mudserver.network.message.ingame;

import java.util.List;

import fr.idev.mudserver.domain.world.ZoneInstance;
import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;
import fr.idev.mudserver.network.server.tui.JsonOutput;

/**
 * Carte statique complète d'une zone (terrain praticable/bloqué + portails),
 * envoyée une fois au client à l'entrée de la zone (voir Portal,
 * CharacterCreate, CharacterSelect) — contrairement à {@link ViewAround},
 * rejouée à chaque tick avec uniquement le viewport dynamique (occupants,
 * chemin) autour du joueur.
 */
public record ZoneMap(ZoneInstance zone) implements OutputTelnetMessage, OutputJsonMessage {

    public record CellView(int q, int r, boolean walkable) {
    }

    public record PortalView(int q, int r, String direction, String targetZoneName) {
    }

    public record Payload(String zoneId, String zoneName, List<CellView> cells, List<PortalView> portals) {
    }

    @Override
    public void toJson(JsonOutput output) {
        List<CellView> cells = zone.getTerrain().entrySet().stream()
                .map(entry -> new CellView(entry.getKey().q(), entry.getKey().r(), entry.getValue().isWalkable()))
                .toList();
        List<PortalView> portals = zone.getPortals().stream().map(portal -> new PortalView(portal.cell().q(),
                portal.cell().r(), portal.direction(), portal.targetZone().getName())).toList();

        output.write("ZoneMap", new Payload(zone.getId().toString(), zone.getName(), cells, portals), false);
    }

    @Override
    public void toTelnet(TelnetOutput output) {
        long blocked = zone.getTerrain().values().stream().filter(tile -> !tile.isWalkable()).count();
        output.write(String.format("Carte de zone chargée : %d cases (%d bloquées), %d portails.%n",
                zone.getTerrain().size(), blocked, zone.getPortals().size()));
    }
}
