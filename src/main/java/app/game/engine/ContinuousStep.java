package app.game.engine;

import app.domain.map.Position;
import app.domain.world.CollisionGrid;

import java.util.List;

/**
 * Intégration continue de position, partagée par {@link MovementEngine}
 * (joueurs) et {@link MonsterAiEngine} (monstres) : avance vers le prochain
 * waypoint d'une distance {@code speed * dt} par sous-pas bornés
 * (anti-tunneling à travers un mur d'une cellule), en s'arrêtant à la dernière
 * position praticable en cas de blocage.
 */
public final class ContinuousStep {

    private static final double MAX_DT_SECONDS = 0.25;
    private static final double EPSILON = 1e-6;

    private ContinuousStep() {
    }

    public record StepResult(Position position, List<Position> remainingWaypoints, boolean blocked) {
    }

    public static StepResult step(Position current, List<Position> waypoints, double unitsPerSecond, double dtSeconds,
            CollisionGrid grid) {
        double clampedDt = Math.min(dtSeconds, MAX_DT_SECONDS);
        double budget = unitsPerSecond * Math.max(0, clampedDt);
        double subStepSize = grid.cellSize() / 4.0;

        Position position = current;
        List<Position> remaining = waypoints;

        while (budget > EPSILON && !remaining.isEmpty()) {
            Position target = remaining.get(0);
            double distanceToTarget = position.distanceTo(target);
            if (distanceToTarget < EPSILON) {
                remaining = remaining.subList(1, remaining.size());
                continue;
            }

            double subStep = Math.min(budget, Math.min(subStepSize, distanceToTarget));
            Position candidate = position.moveToward(target, subStep);
            if (!grid.isWalkable(candidate)) {
                return new StepResult(position, remaining, true);
            }

            position = candidate;
            budget -= subStep;
            if (position.distanceTo(target) < EPSILON) {
                position = target;
                remaining = remaining.subList(1, remaining.size());
            }
        }

        return new StepResult(position, remaining, false);
    }
}
