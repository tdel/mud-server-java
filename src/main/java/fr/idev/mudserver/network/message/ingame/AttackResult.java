package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.game.CombatResult;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record AttackResult(CombatResult result) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        String disadvantage = result.disadvantage() ? " (disadvantage)" : "";
        if (!result.hit()) {
            output.write(String.format("You attack the %s: %d vs AC %d%s — MISS.\n", Ansi.monster(result.targetName()),
                    result.attackRoll(), result.armorClass(), disadvantage));
            return;
        }

        String critical = result.criticalHit() ? " " + Ansi.critical("Critical hit!") : "";
        output.write(String.format("You attack the %s: %d vs AC %d%s — HIT!%s You deal %s damage.\n",
                Ansi.monster(result.targetName()), result.attackRoll(), result.armorClass(), disadvantage, critical,
                Ansi.damage(result.damage())));
    }
}
