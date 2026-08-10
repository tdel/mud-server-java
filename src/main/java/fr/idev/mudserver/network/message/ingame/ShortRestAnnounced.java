package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

/**
 * Envoyé à tous les joueurs en ligne — un repos affecte l'ensemble du monde,
 * pas seulement l'initiateur (voir {@code game.actor.RestService}).
 */
public record ShortRestAnnounced(String initiatorName) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write(Ansi.player(initiatorName) + " calls for a short rest. Everyone catches their breath...\n");
    }
}
