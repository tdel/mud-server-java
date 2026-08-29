package app.domain.actor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.domain.actor.instance.CharacterInstance;
import app.domain.world.MapInstance;
import app.network.message.ingame.EntityAppeared;
import app.network.message.ingame.EntityDisappeared;
import app.network.message.ingame.EntityKind;
import app.network.message.ingame.EntityView;

/**
 * Ensemble des entités actuellement visibles (à portée de perception) pour un
 * {@link AbstractCharacter}. La relation est symétrique : si A connaît B, B
 * connaît A — une seule instance par personnage suffit, chaque mise à jour
 * touche donc toujours les deux côtés.
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
     * Recalcule les entités à portée après un déplacement et notifie les
     * personnages concernés des apparitions/disparitions (seule méthode qui émet
     * EntityAppeared/EntityDisappeared).
     */
    public void refresh() {
        MapInstance map = owner.getCurrentMap();
        synchronized (map) {
            Set<AbstractCharacter> current = nearbyOthers(map);
            for (AbstractCharacter other : current) {
                if (known.add(other)) {
                    other.getKnownList().known.add(owner);
                    notifyAppeared(other);
                }
            }
            for (AbstractCharacter other : List.copyOf(known)) {
                if (!current.contains(other)) {
                    known.remove(other);
                    other.getKnownList().known.remove(owner);
                    notifyDisappeared(other);
                }
            }
        }
    }

    private Set<AbstractCharacter> nearbyOthers(MapInstance map) {
        Set<AbstractCharacter> nearby = new HashSet<>(map.occupantsWithin(owner.getPosition(), AWARENESS_RANGE));
        nearby.remove(owner);
        return nearby;
    }

    private void notifyAppeared(AbstractCharacter other) {
        sendAppearedIfPlayer(owner, other);
        sendAppearedIfPlayer(other, owner);
    }

    private void notifyDisappeared(AbstractCharacter other) {
        sendDisappearedIfPlayer(owner, other);
        sendDisappearedIfPlayer(other, owner);
    }

    private static void sendAppearedIfPlayer(AbstractCharacter maybePlayer, AbstractCharacter subject) {
        if (maybePlayer instanceof CharacterInstance player) {
            player.send(new EntityAppeared(EntityKind.of(subject), EntityView.of(subject)));
        }
    }

    private static void sendDisappearedIfPlayer(AbstractCharacter maybePlayer, AbstractCharacter subject) {
        if (maybePlayer instanceof CharacterInstance player) {
            player.send(new EntityDisappeared(subject.getId(), EntityKind.of(subject)));
        }
    }
}
