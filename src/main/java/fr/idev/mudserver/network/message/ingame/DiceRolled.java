package fr.idev.mudserver.network.message.ingame;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.game.dice.DiceExpression;
import fr.idev.mudserver.game.dice.DiceRoll;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record DiceRolled(DiceExpression expression, DiceRoll result) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        String modifier = switch (Integer.signum(result.modifier())) {
            case 1 -> " + " + result.modifier();
            case -1 -> " - " + Math.abs(result.modifier());
            default -> "";
        };

        String rolls = IntStream.of(result.rolls()).mapToObj(String::valueOf).collect(Collectors.joining(", "));

        output.write(String.format("You roll %s: %s = %d\n", expression, Ansi.dice("[" + rolls + "]" + modifier),
                result.total()));
    }
}
