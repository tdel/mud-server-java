package fr.idev.mudserver.network.message.ingame;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import fr.idev.mudserver.telnet.TelnetOutput;

import static org.assertj.core.api.Assertions.assertThat;

class RoomDescriptionTest {

    private static final Pattern ANSI_CODE = Pattern.compile("\u001B\\[[0-9;]*m");

    @Test
    void toTelnetPreservesGridGeometryAndLegendContentOnceColorIsStripped() {
        List<String> gridLines = List.of(" p #", "n @ m", " . .");
        String legend = "@ = you   p = other player   m = monster   n = npc   # = portal   . = floor   "
                + "~ = out of bounds";
        RoomDescription description = new RoomDescription("Testing Grounds", "A bare room.", gridLines, legend,
                List.of(), List.of(), List.of(), List.of(), List.of());

        StringBuilder captured = new StringBuilder();
        TelnetOutput output = captured::append;
        description.toTelnet(output);

        String stripped = stripAnsi(captured.toString());

        assertThat(stripped).contains(String.join("\n", gridLines));
        assertThat(stripped).contains(legend);
        assertThat(stripped).contains("Portals: none.");
        assertThat(stripped).contains("Characters here: no one else.");
        assertThat(stripped).contains("Items: none.");
        assertThat(stripped).contains("Monsters: none.");
        assertThat(stripped).contains("NPCs: none.");
    }

    @Test
    void toTelnetIncludesTheMapHeader() {
        RoomDescription description = new RoomDescription("Testing Grounds", "A bare room.", List.of(" @ "), "",
                List.of(), List.of(), List.of(), List.of(), List.of());

        StringBuilder captured = new StringBuilder();
        TelnetOutput output = captured::append;
        description.toTelnet(output);

        assertThat(stripAnsi(captured.toString())).contains("Map");
    }

    private static String stripAnsi(String text) {
        return ANSI_CODE.matcher(text).replaceAll("");
    }
}
