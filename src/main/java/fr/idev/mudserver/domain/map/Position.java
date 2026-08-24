package fr.idev.mudserver.domain.map;

public record Position(double x, double y) {

    public double distanceTo(Position other) {
        return Math.hypot(x - other.x, y - other.y);
    }

    public Position plus(Position delta) {
        return new Position(x + delta.x, y + delta.y);
    }

    public Position minus(Position other) {
        return new Position(x - other.x, y - other.y);
    }

    public Position scaled(double factor) {
        return new Position(x * factor, y * factor);
    }

    public double length() {
        return Math.hypot(x, y);
    }

    public Position normalized() {
        double length = length();
        if (length == 0) {
            return new Position(0, 0);
        }
        return scaled(1 / length);
    }

    public Position moveToward(Position target, double maxDistance) {
        Position delta = target.minus(this);
        double distance = delta.length();
        if (distance <= maxDistance) {
            return target;
        }
        return plus(delta.normalized().scaled(maxDistance));
    }
}
