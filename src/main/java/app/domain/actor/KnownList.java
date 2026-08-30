package app.domain.actor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.domain.world.MapInstance;

/**
 * Ensemble des entités actuellement visibles (à portée de perception) pour un
 * {@link AbstractCharacter}. La relation est symétrique : si A connaît B, B
 * connaît A — une seule instance par personnage suffit, chaque mise à jour
 * touche donc toujours les deux côtés. Sert uniquement à borner l'audience des
 * diffusions de mouvement/combat/chat (bande passante, voir
 * {@link AbstractCharacter#broadcast}) : la présence d'une entité sur la carte
 * (donc sa sélectionnabilité) est connue de tous dès l'arrivée sur la carte et
 * à chaque changement réel (join/leave/spawn/mort), indépendamment de cette
 * portée — voir MapEnter et {@link AbstractCharacter#broadcastToMap}.
 */
public final class KnownList {

    public static final double AWARENESS_RANGE = 25.0;

    private final AbstractCharacter owner;
    private final Set<AbstractCharacter> known = ConcurrentHashMap.newKeySet();

    public KnownList(AbstractCharacter owner) {
        this.owner = owner;
    }

    public List<AbstractCharacter> asList() {
        return List.copyOf(known);
    }

    /**
     * Peuplement bidirectionnel initial, sans notification générique (voir
     * join/spawn).
     */
    public void populateSilently() {
        MapInstance map = owner.getCurrentMap();
        synchronized (map) {
            for (AbstractCharacter other : nearbyOthers(map)) {
                known.add(other);
                other.getKnownList().known.add(owner);
            }
        }
    }

    /** Retrait bidirectionnel complet (voir leave/disconnect/mort de monstre). */
    public void clear() {
        MapInstance map = owner.getCurrentMap();
        synchronized (map) {
            for (AbstractCharacter other : known) {
                other.getKnownList().known.remove(owner);
            }
            known.clear();
        }
    }

    /**
     * Recalcule les entités à portée après un déplacement, pour les diffusions de
     * mouvement/combat/chat scopées (voir {@link AbstractCharacter#broadcast}) —
     * n'émet plus EntityAppeared/EntityDisappeared depuis que la présence d'une
     * entité sur la carte est diffusée à tous indépendamment de cette portée (voir
     * la note de classe ci-dessus).
     */
    public void refresh() {
        MapInstance map = owner.getCurrentMap();
        synchronized (map) {
            Set<AbstractCharacter> current = nearbyOthers(map);
            for (AbstractCharacter other : current) {
                if (known.add(other)) {
                    other.getKnownList().known.add(owner);
                }
            }
            for (AbstractCharacter other : List.copyOf(known)) {
                if (!current.contains(other)) {
                    known.remove(other);
                    other.getKnownList().known.remove(owner);
                }
            }
        }
    }

    private Set<AbstractCharacter> nearbyOthers(MapInstance map) {
        Set<AbstractCharacter> nearby = new HashSet<>(map.occupantsWithin(owner.getPosition(), AWARENESS_RANGE));
        nearby.remove(owner);
        return nearby;
    }
}
