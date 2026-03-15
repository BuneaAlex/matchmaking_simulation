package com.master.matchmaking.model.enums;

/**
 * Represents the type of game mode available in the simulated game.
 * Each game mode uses a different matchmaking algorithm and attracts
 * a different proportion of the total daily queue requests.
 */
public enum GameModeType
{
   RANKED,
   CASUAL,
   TOURNAMENT
}

