package app.network.message.ingame;

import java.util.UUID;

import app.network.OutputJsonMessage;

/**
 * monsterId : le nom seul est ambigu (plusieurs "Fox" en vie sur la même
 * carte), le client s'en sert pour animer la mort du bon monstre.
 */
public record MonsterDefeated(UUID monsterId, String monsterName) implements OutputJsonMessage {

}
