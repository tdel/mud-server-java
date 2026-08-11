package fr.idev.mudserver.network.message.charselect;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record NowPlaying(String characterName) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("Now playing " + Ansi.player(characterName) + ".\n\n");
    }
}
