package fr.idev.mudserver.telnet;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.game.HexGridRenderer;

import static org.assertj.core.api.Assertions.assertThat;

class AnsiTest {

    private static final Pattern ANSI_CODE = Pattern.compile("\u001B\\[[0-9;]*m");

    @Test
    void selfHasADistinctColorFromOtherPlayers() {
        assertThat(Ansi.self("@")).isNotEqualTo(Ansi.player("@"));
    }

    @Test
    void gridLineColorsEachOccupantGlyphWithoutTouchingSpacing() {
        assertThat(stripAnsi(Ansi.gridLine(" p #"))).isEqualTo(" p #");
        assertThat(Ansi.gridLine(" p #")).contains(Ansi.player("p")).contains(Ansi.room("#"));

        assertThat(stripAnsi(Ansi.gridLine("n @ m"))).isEqualTo("n @ m");
        assertThat(Ansi.gridLine("n @ m")).contains(Ansi.npc("n")).contains(Ansi.self("@")).contains(Ansi.monster("m"));
    }

    @Test
    void gridLineLeavesFloorAndOutOfBoundsGlyphsUncolored() {
        assertThat(Ansi.gridLine(" . .")).isEqualTo(" . .");
    }

    @Test
    void gridLegendColorsEveryGlyphTokenWithoutLosingContent() {
        String colored = Ansi.gridLegend(HexGridRenderer.LEGEND);

        assertThat(stripAnsi(colored)).isEqualTo(HexGridRenderer.LEGEND);
        assertThat(colored).contains(Ansi.self("@")).contains(Ansi.player("p")).contains(Ansi.monster("m"))
                .contains(Ansi.npc("n")).contains(Ansi.room("#"));
    }

    private static String stripAnsi(String text) {
        return ANSI_CODE.matcher(text).replaceAll("");
    }
}
