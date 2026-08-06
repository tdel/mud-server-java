package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record EncounterEnded(boolean playersWon) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(playersWon
                ? "The fight is over — no more enemies standing.\n"
                : "The fight is over — no more defenders standing.\n");
    }
}
