package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.game.CombatResult;
import fr.idev.mudserver.telnet.Ansi;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record MonsterAttackBroadcast(String monsterName, CombatResult result) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        if (!result.hit()) {
            output.write(String.format("The %s attacks %s: %d vs AC %d — MISS.\n", Ansi.monster(monsterName),
                    Ansi.player(result.targetName()), result.attackRoll(), result.armorClass()));
            return;
        }

        String critical = result.criticalHit() ? " " + Ansi.critical("Critical hit!") : "";
        output.write(String.format("The %s attacks %s: %d vs AC %d — HIT!%s %s damage.\n", Ansi.monster(monsterName),
                Ansi.player(result.targetName()), result.attackRoll(), result.armorClass(), critical,
                Ansi.damage(result.damage())));
    }
}
