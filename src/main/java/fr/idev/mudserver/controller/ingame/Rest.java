package fr.idev.mudserver.controller.ingame;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.idev.mudserver.config.GameConfig;
import fr.idev.mudserver.controller.ControllerHandler;
import fr.idev.mudserver.domain.item.FoodItem;
import fr.idev.mudserver.domain.item.Item;
import fr.idev.mudserver.domain.item.ItemType;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;
import fr.idev.mudserver.domain.actor.instance.RestOutcome;
import fr.idev.mudserver.network.Connection;
import fr.idev.mudserver.network.ConnectionState;
import fr.idev.mudserver.network.message.Usage;
import fr.idev.mudserver.network.message.ingame.CannotRestInCombat;
import fr.idev.mudserver.network.message.ingame.LongRestCancelled;
import fr.idev.mudserver.network.message.ingame.NoProvisionsAvailable;
import fr.idev.mudserver.network.message.ingame.NoShortRestsLeft;
import fr.idev.mudserver.network.message.ingame.NotEnoughProvisions;
import fr.idev.mudserver.network.message.ingame.ProvisionItemNotFound;
import fr.idev.mudserver.network.message.ingame.ProvisionSelection;

@Component
public class Rest implements ControllerHandler {

    @Override
    public String name() {
        return "rest";
    }

    @Override
    public Set<ConnectionState> states() {
        return Set.of(ConnectionState.INGAME);
    }

    @Override
    public void onReceive(Connection connection, String argument) {
        CharacterInstance character = connection.character();
        String choice = argument.trim();

        switch (choice.toLowerCase()) {
            case "short" -> restShort(connection, character);
            case "long" -> restLong(connection, character);
            default -> connection.send(new Usage("rest <short|long>"));
        }
    }

    private void restShort(Connection connection, CharacterInstance character) {
        switch (character.doShortRest()) {
            case RestOutcome.Rested ignored -> {
            }
            case RestOutcome.InCombat ignored -> connection.send(new CannotRestInCombat());
            case RestOutcome.NoShortRestLeft ignored -> connection.send(new NoShortRestsLeft());
            case RestOutcome.NotEnoughProvisions ignored -> {
                // Jamais renvoyé par doShortRest — seul doLongRest peut échouer sur les
                // provisions.
            }
        }
    }

    private void restLong(Connection connection, CharacterInstance character) {
        if (character.isInCombat()) {
            connection.send(new CannotRestInCombat());
            return;
        }

        List<Item> available = new ArrayList<>(character.getInventory().getCarriedItems().stream()
                .filter(item -> item.getType() == ItemType.FOOD).toList());
        if (available.isEmpty()) {
            connection.send(new NoProvisionsAvailable());
            return;
        }

        promptProvisions(connection, character, available, new ArrayList<>());
    }

    private void promptProvisions(Connection connection, CharacterInstance character, List<Item> available,
            List<Item> selected) {
        int selectedValue = selected.stream().mapToInt(Rest::nutritionValue).sum();
        List<ProvisionSelection.Entry> entries = available.stream()
                .map(item -> new ProvisionSelection.Entry(item.getName(), nutritionValue(item))).toList();

        connection.requestBlocking(
                new ProvisionSelection(entries, selectedValue, GameConfig.LONG_REST_PROVISION_THRESHOLD), line -> {
                    String trimmed = line.trim();

                    if (trimmed.equalsIgnoreCase("cancel")) {
                        connection.send(new LongRestCancelled());
                        return;
                    }
                    if (trimmed.equals("0") || trimmed.equalsIgnoreCase("done")) {
                        finalizeLongRest(connection, character, selected);
                        return;
                    }

                    Optional<Item> chosen = resolveProvision(available, trimmed);
                    if (chosen.isEmpty()) {
                        connection.send(new ProvisionItemNotFound(trimmed));
                        promptProvisions(connection, character, available, selected);
                        return;
                    }

                    available.remove(chosen.get());
                    selected.add(chosen.get());
                    promptProvisions(connection, character, available, selected);
                });
    }

    private Optional<Item> resolveProvision(List<Item> available, String input) {
        try {
            int index = Integer.parseInt(input);
            if (index >= 1 && index <= available.size()) {
                return Optional.of(available.get(index - 1));
            }
        } catch (NumberFormatException ignored) {
            // pas un index numérique : recherche par nom ci-dessous
        }
        return available.stream().filter(item -> item.getName().equalsIgnoreCase(input)).findFirst();
    }

    private void finalizeLongRest(Connection connection, CharacterInstance character, List<Item> selected) {
        switch (character.doLongRest(selected)) {
            case RestOutcome.Rested ignored -> {
            }
            case RestOutcome.InCombat ignored -> connection.send(new CannotRestInCombat());
            case RestOutcome.NoShortRestLeft ignored -> {
                // Jamais renvoyé par doLongRest — seul doShortRest a un plafond d'usages.
            }
            case RestOutcome.NotEnoughProvisions(int totalValue) ->
                connection.send(new NotEnoughProvisions(totalValue, GameConfig.LONG_REST_PROVISION_THRESHOLD));
        }
    }

    private static int nutritionValue(Item item) {
        return ((FoodItem) item.getTemplate()).getNutritionValue();
    }
}
