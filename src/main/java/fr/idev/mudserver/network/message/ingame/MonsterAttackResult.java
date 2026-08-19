package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.game.CombatResult;
import fr.idev.mudserver.network.server.telnet.Ansi;
import fr.idev.mudserver.network.server.telnet.OutputTelnetMessage;
import fr.idev.mudserver.network.server.telnet.TelnetOutput;

public record MonsterAttackResult(String monsterName,
        CombatResult result) implements OutputTelnetMessage, OutputJsonMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        if (!result.hit()) {
            output.write(String.format("The %s attacks you: %d vs AC %d — MISS.\n", Ansi.monster(monsterName),
                    result.attackRoll(), result.armorClass()));
            return;
        }

        String critical = result.criticalHit() ? " " + Ansi.critical("Critical hit!") : "";
        output.write(String.format("The %s attacks you: %d vs AC %d — HIT!%s You take %s damage.\n",
                Ansi.monster(monsterName), result.attackRoll(), result.armorClass(), critical,
                Ansi.damage(result.damage())));
    }
}
