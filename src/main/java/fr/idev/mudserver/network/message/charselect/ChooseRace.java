package fr.idev.mudserver.network.message.charselect;

import java.util.Map;

import fr.idev.mudserver.network.OutputJsonMessage;
import fr.idev.mudserver.domain.actor.Attribute;
import fr.idev.mudserver.domain.actor.Race;

public record ChooseRace(Map<Race, Map<Attribute, Integer>> bonusesByRace) implements OutputJsonMessage {

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
