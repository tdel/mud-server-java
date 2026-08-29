package app.domain.world;

import app.domain.actor.AbstractCharacter;
import app.domain.actor.instance.CharacterInstance;
import app.domain.map.Position;
import app.network.message.ingame.PeaceZoneEntered;
import app.network.message.ingame.PeaceZoneExited;

import java.util.List;

public final class PeaceZone extends AbstractZone {

    private static final String DESCRIPTION = "Vous êtes en zone protégée : combat et mort impossibles ici.";

    private final String name;
    private final List<Position> polygon;

    public PeaceZone(String name, List<Position> polygon) {
        this.name = name;
        this.polygon = List.copyOf(polygon);
    }

    @Override
    public String getName() {
        return name;
    }

    public List<Position> polygon() {
        return polygon;
    }

    // Ray casting (règle pair-impair) : compte les arêtes du polygone que le rayon
    // horizontal partant de position vers +x traverse.
    @Override
    public boolean contains(Position position) {
        boolean inside = false;
        int n = polygon.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Position vi = polygon.get(i);
            Position vj = polygon.get(j);
            boolean crossesRay = (vi.y() > position.y()) != (vj.y() > position.y()) && position.x() < (vj.x() - vi.x())
                    * (position.y() - vi.y()) / (vj.y() - vi.y()) + vi.x();
            if (crossesRay) {
                inside = !inside;
            }
        }
        return inside;
    }

    @Override
    public void onObjectEntering(AbstractCharacter character) {
        if (character instanceof CharacterInstance player) {
            player.send(new PeaceZoneEntered(name, DESCRIPTION));
        }
    }

    @Override
    public void onObjectExiting(AbstractCharacter character) {
        if (character instanceof CharacterInstance player) {
            player.send(new PeaceZoneExited(name));
        }
    }
}
