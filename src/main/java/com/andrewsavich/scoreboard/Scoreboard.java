package com.andrewsavich.scoreboard;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Live scoreboard of football matches in progress.
 *
 * <p>A match is identified by the {@link UUID} returned from {@link #startMatch(String, String)}; the same two teams
 * may play more than once. Implementations must be safe for concurrent use by multiple threads, and the
 * {@link Match} snapshots they return must never change after being handed out.
 */
public interface Scoreboard {

    /**
     * Starts a new match with a 0-0 score and registers it as active.
     *
     * @param homeTeam name of the home team, must not be null or blank
     * @param awayTeam name of the away team, must not be null or blank, and must differ from {@code homeTeam}
     * @return snapshot of the started match, carrying its newly generated identifier
     * @throws IllegalArgumentException if a team name is null, blank, or equals the other team name
     */
    Match startMatch(String homeTeam, String awayTeam);

    /**
     * Replaces the score of an active match. Identifier, teams and start order are preserved.
     *
     * @param matchId   identifier of the active match
     * @param homeScore new home score, must not be negative
     * @param awayScore new away score, must not be negative
     * @return snapshot of the updated match
     * @throws NullPointerException     if {@code matchId} is null
     * @throws IllegalStateException    if no active match has the given identifier
     * @throws IllegalArgumentException if a score is negative
     */
    Match updateScore(UUID matchId, int homeScore, int awayScore);

    /**
     * Finishes an active match and removes it from the scoreboard, so its identifier stops being usable.
     *
     * @param matchId identifier of the active match
     * @return final snapshot of the finished match
     * @throws NullPointerException  if {@code matchId} is null
     * @throws IllegalStateException if no active match has the given identifier
     */
    Match finishMatch(UUID matchId);

    /**
     * Returns the active match with the given identifier.
     *
     * <p>The returned snapshot is a point-in-time view: a concurrent update or {@link #finishMatch(UUID)} may
     * supersede it immediately after the call returns.
     *
     * @param id identifier of the match
     * @return the active match, or an empty {@code Optional} when the identifier was never started or its match
     *         has already been finished
     * @throws NullPointerException if {@code id} is null
     */
    Optional<Match> getMatch(UUID id);

    /**
     * Returns the active matches ordered by total score descending; among equal totals, the most recently
     * started match comes first.
     *
     * @return unmodifiable snapshot of the active matches, detached from the scoreboard, empty when no match is active
     */
    List<Match> summary();
}