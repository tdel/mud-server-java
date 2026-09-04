package app.domain.world;

public enum DayPhase {
    NIGHT, DAWN, DAY, DUSK;

    public static DayPhase at(int hour) {
        if (hour >= GameClock.DAWN_START_HOUR && hour < GameClock.DAWN_END_HOUR) {
            return DAWN;
        }
        if (hour >= GameClock.DAWN_END_HOUR && hour < GameClock.DUSK_START_HOUR) {
            return DAY;
        }
        if (hour >= GameClock.DUSK_START_HOUR && hour < GameClock.DUSK_END_HOUR) {
            return DUSK;
        }
        return NIGHT;
    }
}
