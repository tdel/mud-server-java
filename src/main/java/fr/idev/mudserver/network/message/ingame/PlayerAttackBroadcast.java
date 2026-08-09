package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.game.CombatResult;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record PlayerAttackBroadcast(String attackerName, CombatResult result) implements OutputTelnetMessage {

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
