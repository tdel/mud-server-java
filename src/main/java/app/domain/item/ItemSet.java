package app.domain.item;

import java.util.Map;

import app.domain.actor.ModifiedStat;

public record ItemSet(String id, String name, Map<Integer, Map<ModifiedStat, Integer>> bonusByPieceCount) {
}
