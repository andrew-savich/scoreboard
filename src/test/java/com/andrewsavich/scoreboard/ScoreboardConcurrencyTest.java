package com.andrewsavich.scoreboard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreboardConcurrencyTest {

    private static final int THREADS = 16;

    private Scoreboard scoreboard;
    private ExecutorService executor;
    private ConcurrentLinkedQueue<Throwable> exceptions;

    @BeforeEach
    void setUp() {
        scoreboard = new InMemoryScoreboard();
        executor = Executors.newFixedThreadPool(THREADS);
        exceptions = new ConcurrentLinkedQueue<>();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void concurrentScoreUpdatesMaintainStateIntegrity() throws InterruptedException {
        Match match = scoreboard.startMatch("Argentina", "Brazil");
        int writers = 8;
        int updatesPerWriter = 250;
        AtomicInteger writes = new AtomicInteger();

        runConcurrently(writers, writer -> {
            for (int i = 0; i < updatesPerWriter; i++) {
                int value = writes.incrementAndGet();
                Match updated = scoreboard.updateScore(match.id(), 2 * value, 2 * value + 1);

                assertThat(updated.homeScore()).isEqualTo(2 * value);
                assertThat(updated.awayScore()).isEqualTo(2 * value + 1);
            }
        });

        assertThat(exceptions).isEmpty();
        assertThat(writes).hasValue(writers * updatesPerWriter);

        List<Match> summary = scoreboard.summary();
        Match finalState = summary.getFirst();

        assertThat(summary).hasSize(1);
        assertThat(finalState.id()).isEqualTo(match.id());
        assertThat(finalState.homeTeam()).isEqualTo("Argentina");
        assertThat(finalState.awayTeam()).isEqualTo("Brazil");
        assertThat(finalState.sequence()).isEqualTo(match.sequence());
        assertThat(finalState.awayScore()).isEqualTo(finalState.homeScore() + 1);
        assertThat(finalState.homeScore()).isEven().isBetween(2, 2 * writers * updatesPerWriter);
    }

    @Test
    void concurrentOperationsDoNotThrowOrCorruptState() throws InterruptedException {
        int starters = 4;
        int churners = 4;
        int readers = 4;
        int operationsPerThread = 200;
        List<UUID> activeIds = new CopyOnWriteArrayList<>();

        runConcurrently(starters + churners + readers, index -> {
            if (index < starters) {
                for (int i = 0; i < operationsPerThread; i++) {
                    activeIds.add(scoreboard.startMatch("Home" + index + "-" + i, "Away" + index + "-" + i).id());
                }
            } else if (index < starters + churners) {
                for (int i = 0; i < operationsPerThread && !activeIds.isEmpty(); i++) {
                    UUID id = activeIds.get(ThreadLocalRandom.current().nextInt(activeIds.size()));

                    if (i % 2 == 0) {
                        try {
                            scoreboard.updateScore(id, i, i);
                        } catch (IllegalStateException ignored) {
                        }
                    } else {
                        try {
                            scoreboard.finishMatch(id);
                        } catch (IllegalStateException ignored) {
                        }
                    }
                }
            } else {
                for (int i = 0; i < operationsPerThread; i++) {
                    verifyConsistentSnapshot(scoreboard.summary());
                }
            }
        });

        assertThat(exceptions).isEmpty();

        List<Match> summary = scoreboard.summary();

        verifyConsistentSnapshot(summary);
        assertThat(summary).hasSizeLessThanOrEqualTo(starters * operationsPerThread);
    }

    @Test
    void concurrentStartMatchAssignsUniqueSequences() throws InterruptedException {
        int threads = THREADS;
        int matchesPerThread = 100;
        ConcurrentLinkedQueue<Match> created = new ConcurrentLinkedQueue<>();

        runConcurrently(threads, index -> {
            for (int i = 0; i < matchesPerThread; i++) {
                created.add(scoreboard.startMatch("Home" + index + "-" + i, "Away" + index + "-" + i));
            }
        });

        assertThat(exceptions).isEmpty();
        assertThat(created).hasSize(threads * matchesPerThread);
        assertThat(created).extracting(Match::sequence).doesNotHaveDuplicates().hasSize(threads * matchesPerThread);
        assertThat(created).extracting(Match::id).doesNotHaveDuplicates();
        assertThat(scoreboard.summary()).hasSize(threads * matchesPerThread);
    }

    @Test
    void concurrentGetMatchSeesOnlyConsistentSnapshots() throws InterruptedException {
        int matchCount = 16;
        int writers = 8;
        int readers = 4;
        int operationsPerThread = 200;
        List<UUID> ids = new CopyOnWriteArrayList<>();
        AtomicInteger writes = new AtomicInteger();

        for (int i = 0; i < matchCount; i++) {
            Match match = scoreboard.startMatch("Home" + i, "Away" + i);
            scoreboard.updateScore(match.id(), 2, 3);
            ids.add(match.id());
        }

        runConcurrently(writers + readers, index -> {
            if (index < writers) {
                for (int i = 0; i < operationsPerThread; i++) {
                    int value = writes.incrementAndGet();
                    scoreboard.updateScore(ids.get(ThreadLocalRandom.current().nextInt(ids.size())),
                                           2 * value, 2 * value + 1);
                }
            } else {
                for (int i = 0; i < operationsPerThread; i++) {
                    UUID id = ids.get(ThreadLocalRandom.current().nextInt(ids.size()));
                    Match found = scoreboard.getMatch(id).orElseThrow();

                    assertThat(found.id()).isEqualTo(id);
                    assertThat(found.awayScore()).isEqualTo(found.homeScore() + 1);
                }
            }
        });

        assertThat(exceptions).isEmpty();
        assertThat(writes).hasValue(writers * operationsPerThread);
    }
    private void runConcurrently(int threadCount, IntConsumer task) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        IntStream.range(0, threadCount).forEach(index -> executor.execute(() -> {
            try {
                startGate.await();
                task.accept(index);
            } catch (Throwable failure) {
                exceptions.add(failure);
            } finally {
                done.countDown();
            }
        }));

        startGate.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).as("all worker threads completed").isTrue();
    }

    private static void verifyConsistentSnapshot(List<Match> summary) {
        assertThat(summary).extracting(Match::id).doesNotHaveDuplicates();
        assertThat(summary).extracting(Match::sequence).doesNotHaveDuplicates();
        assertThat(summary).allSatisfy(match -> {
            assertThat(match.id()).isNotNull();
            assertThat(match.homeTeam()).isNotEqualTo(match.awayTeam());
            assertThat(match.homeScore()).isNotNegative();
            assertThat(match.awayScore()).isNotNegative();
        });

        for (int i = 1; i < summary.size(); i++) {
            Match previous = summary.get(i - 1);
            Match current = summary.get(i);
            int previousTotal = previous.homeScore() + previous.awayScore();
            int currentTotal = current.homeScore() + current.awayScore();

            assertThat(previousTotal).isGreaterThanOrEqualTo(currentTotal);

            if (previousTotal == currentTotal) {
                assertThat(previous.sequence()).isGreaterThan(current.sequence());
            }
        }
    }
}