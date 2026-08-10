package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

/**
 * Poussé au participant dont c'est le tour dès que la cascade de
 * {@code CombatEngine} s'arrête sur lui — nécessaire car le combat n'a aucune
 * boucle de fond : sans ce message, un joueur dont le tour vient d'arriver
 * (après une riposte de monstre déclenchée par l'action d'un autre joueur, sur
 * le thread de ce dernier) n'aurait aucun signal qu'il doit agir.
 */
public record YourTurn(int actionsRemaining, int extraActionsRemaining) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        String extra = extraActionsRemaining > 0 ? " and " + extraActionsRemaining + " extra action(s)" : "";
        output.write("It's your turn! You have " + actionsRemaining + " action(s)" + extra
                + ". attack <target> or use <item>.\n");
    }
}
