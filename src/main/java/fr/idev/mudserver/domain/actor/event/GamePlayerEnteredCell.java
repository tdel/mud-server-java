package fr.idev.mudserver.domain.actor.event;

import fr.idev.mudserver.domain.map.HexCoordinate;
import fr.idev.mudserver.domain.actor.instance.CharacterInstance;

public record GamePlayerEnteredCell(CharacterInstance character, HexCoordinate cell) {
}
