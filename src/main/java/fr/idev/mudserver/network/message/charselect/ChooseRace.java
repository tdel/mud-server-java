package fr.idev.mudserver.network.message.charselect;

import java.util.Map;

import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.Race;
import fr.idev.mudserver.telnet.OutputTelnetMessage;
import fr.idev.mudserver.telnet.TelnetOutput;

public record ChooseRace(Map<Race, Map<Attribute, Integer>> bonusesByRace) implements OutputTelnetMessage {

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
        Map<Attribute, Integer> bonuses = bonusesByRace.get(race);

        if (bonuses.size() == 6) {
            int bonus = bonuses.values().iterator().next();
            return "+" + bonus + " to all six attributes";
        }

        StringBuilder parts = new StringBuilder();
        for (Map.Entry<Attribute, Integer> entry : bonuses.entrySet()) {
            if (!parts.isEmpty()) {
                parts.append(", ");
            }
            parts.append("+").append(entry.getValue()).append(" ").append(entry.getKey().label());
        }
        return parts.toString();
    }
}
