package fr.idev.mudserver.game.dice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record DiceExpression(int count, int sides, int modifier) {

    private static final Pattern NOTATION = Pattern.compile("^(\\d*)d(\\d+)([+-]\\d+)?$");

    public static DiceExpression parse(String notation) {
        String normalized = notation.strip().toLowerCase();
        Matcher matcher = NOTATION.matcher(normalized);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid dice notation: \"" + notation + "\".");
        }

        String countGroup = matcher.group(1);
        int count = countGroup.isEmpty() ? 1 : Integer.parseInt(countGroup);
        int sides = Integer.parseInt(matcher.group(2));

        if (count < 1 || sides < 1) {
            throw new IllegalArgumentException("Invalid dice notation: \"" + notation + "\".");
        }

        String modifierGroup = matcher.group(3);
        int modifier = modifierGroup == null ? 0 : Integer.parseInt(modifierGroup);

        return new DiceExpression(count, sides, modifier);
    }

    @Override
    public String toString() {
        String modifierText = switch (Integer.signum(modifier)) {
            case 1 -> "+" + modifier;
            case -1 -> String.valueOf(modifier);
            default -> "";
        };
        return count + "d" + sides + modifierText;
    }
}
