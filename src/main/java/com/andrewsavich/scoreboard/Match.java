package com.andrewsavich.scoreboard;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of a single match at a point in time. Every score change produces a new instance.
 *
 * @param id        unique identifier of the match, generated when the match is started
 * @param homeTeam  name of the home team
 * @param awayTeam  name of the away team
 * @param homeScore current score of the home team
 * @param awayScore current score of the away team
 * @param sequence  start order of the match, strictly increasing per scoreboard
 */
public record Match(UUID id,
                    String homeTeam,
                    String awayTeam,
                    int homeScore,
                    int awayScore,
                    long sequence) {

    /**
     * Creates a match, rejecting null identifiers, blank or identical team names and negative scores.
     *
     * @throws NullPointerException     if {@code id} is null
     * @throws IllegalArgumentException if a team name is null, blank or identical to the other, or a score is negative
     */
    public Match {
        Objects.requireNonNull(id, "id");
        if (homeTeam == null || homeTeam.isBlank()) {
            throw new IllegalArgumentException("homeTeam must not be null or blank");
        }
        if (awayTeam == null || awayTeam.isBlank()) {
            throw new IllegalArgumentException("awayTeam must not be null or blank");
        }
        if (homeTeam.equals(awayTeam)) {
            throw new IllegalArgumentException("homeTeam and awayTeam must differ");
        }
        if (homeScore < 0 || awayScore < 0) {
            throw new IllegalArgumentException("score must not be negative");
        }
    }
}