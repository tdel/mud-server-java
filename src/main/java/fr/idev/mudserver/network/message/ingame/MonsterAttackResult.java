package fr.idev.mudserver.network.message.ingame;

import fr.idev.mudserver.game.CombatResult;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record MonsterAttackResult(String monsterName, CombatResult result) implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        if (!result.hit()) {
            output.write(String.format("The %s attacks you: %d vs AC %d — MISS.\n", monsterName, result.attackRoll(),
                    result.armorClass()));
            return;
        }

        String critical = result.criticalHit() ? " Critical hit!" : "";
        output.write(String.format("The %s attacks you: %d vs AC %d — HIT!%s You take %d damage.\n", monsterName,
                result.attackRoll(), result.armorClass(), critical, result.damage()));
    }
}
