package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.game.CombatResult;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record PlayerAttackBroadcast(String attackerName,
        CombatResult result) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        String disadvantage = result.disadvantage() ? " (disadvantage)" : "";
        if (!result.hit()) {
            output.write(String.format("%s attacks the %s: %d vs AC %d%s — MISS.\n", Ansi.player(attackerName),
                    Ansi.monster(result.targetName()), result.attackRoll(), result.armorClass(), disadvantage));
            return;
        }

        String critical = result.criticalHit() ? " " + Ansi.critical("Critical hit!") : "";
        output.write(String.format("%s attacks the %s: %d vs AC %d%s — HIT!%s %s damage.\n", Ansi.player(attackerName),
                Ansi.monster(result.targetName()), result.attackRoll(), result.armorClass(), disadvantage, critical,
                Ansi.damage(result.damage())));
    }
}
