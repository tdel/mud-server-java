package app.domain.item;

import java.util.List;

public record LootResult(int gold, List<Item> items) {
}
