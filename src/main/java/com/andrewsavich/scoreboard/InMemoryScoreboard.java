package com.andrewsavich.scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory {@link Scoreboard} implementation.
 *
 * <p>Active matches are held in a {@link HashMap} guarded by a {@link ReentrantReadWriteLock}:
 * {@link #summary()} copies the values under the read lock and sorts the detached copy afterwards, so readers
 * do not exclude each other and sorting never blocks writers.
 */
public final class InMemoryScoreboard implements Scoreboard {

    private static final Comparator<Match> SUMMARY_ORDER =
            Comparator.comparingLong((Match match) -> (long) match.homeScore() + match.awayScore())
                      .thenComparingLong(Match::sequence)
                      .reversed();

    private final Map<UUID, Match> matches = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private long nextSequence = 0L;

    /**
     * Creates an empty scoreboard that holds no active matches.
     */
    public InMemoryScoreboard() {
    }

    @Override
    public Match startMatch(String homeTeam, String awayTeam) {
        lock.writeLock().lock();
        try {
            Match match = new Match(UUID.randomUUID(), homeTeam, awayTeam, 0, 0, nextSequence + 1);
            nextSequence = match.sequence();
            matches.put(match.id(), match);
            return match;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Match updateScore(UUID matchId, int homeScore, int awayScore) {
        Objects.requireNonNull(matchId, "matchId");
        if (homeScore < 0 || awayScore < 0) {
            throw new IllegalArgumentException("score must not be negative");
        }
        lock.writeLock().lock();
        try {
            Match currentMatch = matches.get(matchId);
            if (currentMatch == null) {
                throw new IllegalStateException("No active match for id " + matchId);
            }
            Match updated = new Match(currentMatch.id(), currentMatch.homeTeam(), currentMatch.awayTeam(),
                                      homeScore, awayScore, currentMatch.sequence());
            matches.put(matchId, updated);
            return updated;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Match finishMatch(UUID matchId) {
        Objects.requireNonNull(matchId, "matchId");
        lock.writeLock().lock();
        try {
            Match removedMatch = matches.remove(matchId);
            if (removedMatch == null) {
                throw new IllegalStateException("No active match for id " + matchId);
            }
            return removedMatch;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<Match> getMatch(UUID id) {
        Objects.requireNonNull(id, "id");
        lock.readLock().lock();
        try {
            return Optional.ofNullable(matches.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Match> summary() {
        List<Match> snapshot;
        lock.readLock().lock();
        try {
            snapshot = new ArrayList<>(matches.values());
        } finally {
            lock.readLock().unlock();
        }
        snapshot.sort(SUMMARY_ORDER);
        return List.copyOf(snapshot);
    }
}