package fr.idev.mudserver.game.dice;

/**
 * @param rolls
 *            résultat de chaque dé individuel, avant application du
 *            modificateur
 */
public record DiceRoll(int[] rolls, int modifier) {

    public int total() {
        int sum = modifier;
        for (int roll : rolls) {
            sum += roll;
        }
        return sum;
    }
}
