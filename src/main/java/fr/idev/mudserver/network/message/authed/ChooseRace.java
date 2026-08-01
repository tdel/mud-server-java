package fr.idev.mudserver.network.message.authed;

import java.util.Map;

import fr.idev.mudserver.domain.Ability;
import fr.idev.mudserver.domain.Race;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ChooseRace() implements OutputTelnetMessage {

    @Override
    public void toTelnet(TelnetOutput output) {
        StringBuilder text = new StringBuilder("Choose your character's race:");
        for (Race race : Race.values()) {
            text.append("\n  ").append(race.label()).append(" - ").append(describeBonuses(race));
        }
        text.append("\n");
        output.write(text.toString());
    }

    private String describeBonuses(Race race) {
        Map<Ability, Integer> bonuses = race.abilityScoreBonuses();

        if (bonuses.size() == 6) {
            int bonus = bonuses.values().iterator().next();
            return "+" + bonus + " to all six abilities";
        }

        StringBuilder parts = new StringBuilder();
        for (Map.Entry<Ability, Integer> entry : bonuses.entrySet()) {
            if (!parts.isEmpty()) {
                parts.append(", ");
            }
            parts.append("+").append(entry.getValue()).append(" ").append(entry.getKey().label());
        }
        return parts.toString();
    }
}
