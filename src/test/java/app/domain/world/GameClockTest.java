package app.domain.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class GameClockTest {

    private static final long REAL_MILLIS_PER_GAME_HOUR = GameClock.REAL_MILLIS_PER_GAME_HOUR;

    private static long realMillisAtGameHour(int gameHour) {
        return gameHour * REAL_MILLIS_PER_GAME_HOUR;
    }

    @Test
    void justBeforeDawnIsNight() {
        GameTime time = GameClock.at(realMillisAtGameHour(GameClock.DAWN_START_HOUR) - 1);
        assertThat(time.phase()).isEqualTo(DayPhase.NIGHT);
    }

    @Test
    void dawnStartIsDawn() {
        GameTime time = GameClock.at(realMillisAtGameHour(GameClock.DAWN_START_HOUR));
        assertThat(time.phase()).isEqualTo(DayPhase.DAWN);
        assertThat(time.hour()).isEqualTo(GameClock.DAWN_START_HOUR);
    }

    @Test
    void justBeforeDawnEndIsStillDawn() {
        GameTime time = GameClock.at(realMillisAtGameHour(GameClock.DAWN_END_HOUR) - 1);
        assertThat(time.phase()).isEqualTo(DayPhase.DAWN);
    }

    @Test
    void dawnEndIsFullDay() {
        GameTime time = GameClock.at(realMillisAtGameHour(GameClock.DAWN_END_HOUR));
        assertThat(time.phase()).isEqualTo(DayPhase.DAY);
    }

    @Test
    void justBeforeDuskIsStillDay() {
        GameTime time = GameClock.at(realMillisAtGameHour(GameClock.DUSK_START_HOUR) - 1);
        assertThat(time.phase()).isEqualTo(DayPhase.DAY);
    }

    @Test
    void duskStartIsDusk() {
        GameTime time = GameClock.at(realMillisAtGameHour(GameClock.DUSK_START_HOUR));
        assertThat(time.phase()).isEqualTo(DayPhase.DUSK);
        assertThat(time.hour()).isEqualTo(GameClock.DUSK_START_HOUR);
    }

    @Test
    void justBeforeDuskEndIsStillDusk() {
        GameTime time = GameClock.at(realMillisAtGameHour(GameClock.DUSK_END_HOUR) - 1);
        assertThat(time.phase()).isEqualTo(DayPhase.DUSK);
    }

    @Test
    void duskEndIsFullNight() {
        GameTime time = GameClock.at(realMillisAtGameHour(GameClock.DUSK_END_HOUR));
        assertThat(time.phase()).isEqualTo(DayPhase.NIGHT);
    }

    @Test
    void cycleWrapsAroundToMidnight() {
        GameTime time = GameClock.at(GameClock.CYCLE_DURATION_MS);
        assertThat(time.hour()).isEqualTo(0);
        assertThat(time.minute()).isEqualTo(0);
        assertThat(time.phase()).isEqualTo(DayPhase.NIGHT);
    }

    @Test
    void cycleDurationMatchesEightRealHours() {
        assertThat(GameClock.CYCLE_DURATION_MS).isEqualTo(Duration.ofHours(8).toMillis());
    }
}
