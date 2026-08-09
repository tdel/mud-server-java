package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.game.CombatResult;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record AttackResult(CombatResult result) implements OutputTelnetMessage {

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
