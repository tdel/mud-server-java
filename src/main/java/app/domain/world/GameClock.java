package app.domain.world;

import java.time.Duration;

public final class GameClock {

    public static final long CYCLE_DURATION_MS = Duration.ofHours(8).toMillis();
    private static final int GAME_HOURS_PER_CYCLE = 24;
    public static final long REAL_MILLIS_PER_GAME_HOUR = CYCLE_DURATION_MS / GAME_HOURS_PER_CYCLE;

    public static final int DAWN_START_HOUR = 7;
    public static final int DAWN_END_HOUR = 8;
    public static final int DUSK_START_HOUR = 21;
    public static final int DUSK_END_HOUR = 22;

    private static final long GAME_MILLIS_PER_HOUR = 3_600_000L;
    private static final long GAME_MILLIS_PER_MINUTE = 60_000L;

    private GameClock() {
    }

    public static GameTime now() {
        return at(System.currentTimeMillis());
    }

    static GameTime at(long epochMillis) {
        long elapsed = Math.floorMod(epochMillis, CYCLE_DURATION_MS);
        long gameMillisOfDay = elapsed * GAME_HOURS_PER_CYCLE * GAME_MILLIS_PER_HOUR / CYCLE_DURATION_MS;
        int hour = (int) (gameMillisOfDay / GAME_MILLIS_PER_HOUR) % GAME_HOURS_PER_CYCLE;
        int minute = (int) (gameMillisOfDay / GAME_MILLIS_PER_MINUTE) % 60;
        return new GameTime(hour, minute, DayPhase.at(hour));
    }
}
