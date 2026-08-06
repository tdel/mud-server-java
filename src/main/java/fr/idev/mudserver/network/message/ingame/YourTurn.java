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
public record YourTurn() implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        output.write("It's your turn! attack <target> or use <item>.\n");
    }
}
