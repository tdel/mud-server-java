package app.domain.actor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.domain.MapPortal;
import app.domain.map.Position;
import app.domain.world.MapInstance;
import app.network.message.ingame.EntityAppeared;
import app.network.message.ingame.EntityDisappeared;
import app.network.message.ingame.EntityView;
import app.network.message.ingame.PortalAppeared;
import app.network.message.ingame.PortalDisappeared;
import app.network.message.ingame.PortalView;

/**
 * Ensemble des entités actuellement visibles (à portée de perception) pour un
 * {@link AbstractCharacter}. La relation est symétrique : si A connaît B, B
 * connaît A — une seule instance par personnage suffit, chaque mise à jour
 * touche donc toujours les deux côtés. C'est le seul canal par lequel un client
 * apprend la présence d'une entité sur la carte (voir MapEnter/MapView, qui ne
 * transmettent plus que la carte et l'état du personnage courant) : chaque
 * évolution de cette liste — spawn ({@link #populate}), déplacement
 * ({@link #refresh}), départ définitif ({@link #clear}) — pousse un
 * {@link EntityAppeared}/{@link EntityDisappeared} aux deux parties concernées.
 * Les {@link MapPortal} suivent le même principe mais à sens unique (un portail
 * ne "connaît" pas les personnages en retour) via
 * {@link PortalAppeared}/{@link PortalDisappeared}.
 */
public final class KnownList {

    // Volontairement large (très supérieur à CombatFormulas.ATTACK_RANGE) : la
    // KnownList pilote maintenant tout ce que le client peut afficher, pas
    // seulement la portée des diffusions de mouvement/combat/chat — trop étroite,
    // le joueur ne verrait plus rien apparaître avant d'être quasiment dessus.
    public static final double AWARENESS_RANGE = 40.0;

    private final AbstractCharacter owner;
    private final Set<AbstractCharacter> known = ConcurrentHashMap.newKeySet();
    // Egalité par identité (MapPortal ne redéfinit pas equals/hashCode) : suffit
    // puisque MapInstance.getPortals() mémoïse et renvoie toujours les mêmes
    // instances (voir MapInstance.portals).
    private final Set<MapPortal> knownPortals = ConcurrentHashMap.newKeySet();

    public KnownList(AbstractCharacter owner) {
        this.owner = owner;
    }

    public List<AbstractCharacter> asList() {
        return List.copyOf(known);
    }

    /**
     * Peuplement bidirectionnel initial au spawn (création, sélection de
     * personnage, portail, réapparition) : envoie à owner les entités déjà à
     * portée, et à chacune d'elles l'apparition d'owner.
     */
    public void populate() {
        MapInstance map = owner.getMotionSystem().getCurrentMap();
        Set<AbstractCharacter> nearby;
        Set<MapPortal> nearbyPortals;
        synchronized (map) {
            nearby = nearbyOthers(map);
            for (AbstractCharacter other : nearby) {
                known.add(other);
                other.getKnownList().known.add(owner);
            }
            nearbyPortals = nearbyPortals(map);
            knownPortals.addAll(nearbyPortals);
        }
        notifyAppeared(owner, nearby);
        notifyAppearedToEach(nearby, owner);
        notifyPortalsAppeared(owner, nearbyPortals);
    }

    /** Retrait bidirectionnel complet (voir leave/disconnect/mort de monstre). */
    public void clear() {
        MapInstance map = owner.getMotionSystem().getCurrentMap();
        Set<AbstractCharacter> previouslyKnown;
        synchronized (map) {
            previouslyKnown = Set.copyOf(known);
            for (AbstractCharacter other : previouslyKnown) {
                other.getKnownList().known.remove(owner);
            }
            known.clear();
            knownPortals.clear();
        }
        notifyDisappearedToEach(previouslyKnown, owner);
    }

    /**
     * Recalcule les entités à portée après un déplacement (voir
     * MovementEngine.tick/MonsterAiEngine.stepToward) et pousse
     * EntityAppeared/EntityDisappeared pour ce qui a changé, dans les deux sens.
     */
    public void refresh() {
        MapInstance map = owner.getMotionSystem().getCurrentMap();
        Set<AbstractCharacter> appeared = new HashSet<>();
        Set<AbstractCharacter> disappeared = new HashSet<>();
        Set<MapPortal> portalsAppeared = new HashSet<>();
        Set<MapPortal> portalsDisappeared = new HashSet<>();
        synchronized (map) {
            Set<AbstractCharacter> current = nearbyOthers(map);
            for (AbstractCharacter other : current) {
                if (known.add(other)) {
                    other.getKnownList().known.add(owner);
                    appeared.add(other);
                }
            }
            for (AbstractCharacter other : List.copyOf(known)) {
                if (!current.contains(other)) {
                    known.remove(other);
                    other.getKnownList().known.remove(owner);
                    disappeared.add(other);
                }
            }
            Set<MapPortal> currentPortals = nearbyPortals(map);
            for (MapPortal portal : currentPortals) {
                if (knownPortals.add(portal)) {
                    portalsAppeared.add(portal);
                }
            }
            for (MapPortal portal : List.copyOf(knownPortals)) {
                if (!currentPortals.contains(portal)) {
                    knownPortals.remove(portal);
                    portalsDisappeared.add(portal);
                }
            }
        }
        notifyAppeared(owner, appeared);
        notifyAppearedToEach(appeared, owner);
        notifyDisappeared(owner, disappeared);
        notifyDisappearedToEach(disappeared, owner);
        notifyPortalsAppeared(owner, portalsAppeared);
        notifyPortalsDisappeared(owner, portalsDisappeared);
    }

    private static void notifyAppeared(AbstractCharacter observer, Set<AbstractCharacter> subjects) {
        if (!subjects.isEmpty()) {
            observer.send(new EntityAppeared(subjects.stream().map(EntityView::of).toList()));
        }
    }

    private static void notifyAppearedToEach(Set<AbstractCharacter> observers, AbstractCharacter subject) {
        if (observers.isEmpty()) {
            return;
        }
        EntityAppeared message = new EntityAppeared(List.of(EntityView.of(subject)));
        for (AbstractCharacter observer : observers) {
            observer.send(message);
        }
    }

    private static void notifyDisappeared(AbstractCharacter observer, Set<AbstractCharacter> subjects) {
        if (!subjects.isEmpty()) {
            observer.send(new EntityDisappeared(subjects.stream().map(AbstractCharacter::getId).toList()));
        }
    }

    private static void notifyDisappearedToEach(Set<AbstractCharacter> observers, AbstractCharacter subject) {
        if (observers.isEmpty()) {
            return;
        }
        EntityDisappeared message = new EntityDisappeared(List.of(subject.getId()));
        for (AbstractCharacter observer : observers) {
            observer.send(message);
        }
    }

    private Set<AbstractCharacter> nearbyOthers(MapInstance map) {
        Set<AbstractCharacter> nearby = new HashSet<>(
                map.occupantsWithin(owner.getMotionSystem().getPosition(), AWARENESS_RANGE));
        nearby.remove(owner);
        return nearby;
    }

    // Contrairement aux AbstractCharacter, un portail est statique et ne
    // "connaît" pas owner en retour (pas de KnownList, pas de send()) : la
    // relation reste à sens unique, pas de bidirectionnalité à maintenir ici.
    private Set<MapPortal> nearbyPortals(MapInstance map) {
        Position position = owner.getMotionSystem().getPosition();
        Set<MapPortal> nearby = new HashSet<>();
        for (MapPortal portal : map.getPortals()) {
            if (portal.position().distanceTo(position) <= AWARENESS_RANGE) {
                nearby.add(portal);
            }
        }
        return nearby;
    }

    private static void notifyPortalsAppeared(AbstractCharacter observer, Set<MapPortal> portals) {
        if (!portals.isEmpty()) {
            observer.send(new PortalAppeared(portals.stream().map(PortalView::of).toList()));
        }
    }

    private static void notifyPortalsDisappeared(AbstractCharacter observer, Set<MapPortal> portals) {
        if (!portals.isEmpty()) {
            observer.send(new PortalDisappeared(portals.stream().map(MapPortal::getId).toList()));
        }
    }
}
