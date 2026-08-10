package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

/**
 * Poussé après une action qui n'a pas épuisé le budget de tour de l'acteur
 * (voir {@code game.CombatEngine#continueOrEndTurn}) : le tour continue sans
 * faire avancer {@code CombatEncounter}, donc rien d'autre ne signale au joueur
 * qu'il peut encore agir. Verbe-agnostique, partagé par {@code attack} et
 * {@code use}, et réutilisable telle quelle par une future commande de sort.
 */
public record ActionsRemaining(int actionsRemaining, int extraActionsRemaining) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        String extra = extraActionsRemaining > 0 ? " and " + extraActionsRemaining + " extra action(s)" : "";
        output.write("You have " + actionsRemaining + " action(s)" + extra + " remaining this turn.\n");
    }
}
