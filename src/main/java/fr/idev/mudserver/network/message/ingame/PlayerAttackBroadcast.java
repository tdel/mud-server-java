package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.game.CombatResult;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record PlayerAttackBroadcast(String attackerName, CombatResult result) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        if (!result.hit()) {
            output.write(String.format("%s attacks the %s: %d vs AC %d — MISS.\n", attackerName, result.targetName(),
                    result.attackRoll(), result.armorClass()));
            return;
        }

        String critical = result.criticalHit() ? " Critical hit!" : "";
        output.write(String.format("%s attacks the %s: %d vs AC %d — HIT!%s %d damage.\n", attackerName,
                result.targetName(), result.attackRoll(), result.armorClass(), critical, result.damage()));
    }
}
