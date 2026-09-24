package com.andrewsavich.scoreboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreboardTest {

    private Scoreboard scoreboard;

    @BeforeEach
    void setUp() {
        scoreboard = new InMemoryScoreboard();
    }

    @Test
    void startMatchInitializesScoreToZeroZero() {
        Match match = scoreboard.startMatch("Argentina", "Brazil");

        assertThat(match.homeScore()).isZero();
        assertThat(match.awayScore()).isZero();
    }

    @Test
    void startMatchAssignsUniqueNonNullIds() {
        Match first = scoreboard.startMatch("Argentina", "Brazil");
        Match second = scoreboard.startMatch("Germany", "France");

        assertThat(first.id()).isNotNull();
        assertThat(second.id()).isNotNull();
        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void startMatchAssignsStrictlyIncreasingSequences() {
        Match first = scoreboard.startMatch("Argentina", "Brazil");
        Match second = scoreboard.startMatch("Germany", "France");

        assertThat(second.sequence()).isGreaterThan(first.sequence());
    }

    @Test
    void startMatchAcceptsSameFixtureTwiceWithDistinctIds() {
        Match first = scoreboard.startMatch("Argentina", "Brazil");
        Match second = scoreboard.startMatch("Argentina", "Brazil");

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void startMatchRejectsInvalidTeamNameWithoutConsumingASequence(String awayTeam) {
        Match first = scoreboard.startMatch("Argentina", "Brazil");

        assertThatThrownBy(() -> scoreboard.startMatch("Germany", awayTeam))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(scoreboard.startMatch("Germany", "France").sequence()).isEqualTo(first.sequence() + 1);
    }

    @Test
    void updateScoreUpdatesScoreAndPreservesOtherFields() {
        Match started = scoreboard.startMatch("Argentina", "Brazil");

        Match updated = scoreboard.updateScore(started.id(), 3, 1);

        assertThat(updated.homeScore()).isEqualTo(3);
        assertThat(updated.awayScore()).isEqualTo(1);
        assertThat(updated.id()).isEqualTo(started.id());
        assertThat(updated.homeTeam()).isEqualTo(started.homeTeam());
        assertThat(updated.awayTeam()).isEqualTo(started.awayTeam());
        assertThat(updated.sequence()).isEqualTo(started.sequence());
    }

    @Test
    void updateScoreWithUnknownIdThrowsIllegalStateException() {
        scoreboard.startMatch("Argentina", "Brazil");

        assertThatThrownBy(() -> scoreboard.updateScore(UUID.randomUUID(), 1, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateScoreWithNullIdThrowsNullPointerException() {
        assertThatThrownBy(() -> scoreboard.updateScore(null, 1, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void updateScoreWithNegativeScoreThrowsIllegalArgumentExceptionAndLeavesStateIntact() {
        Match started = scoreboard.startMatch("Argentina", "Brazil");
        scoreboard.updateScore(started.id(), 2, 1);

        assertThatThrownBy(() -> scoreboard.updateScore(started.id(), -1, 2))
                .isInstanceOf(IllegalArgumentException.class);

        List<Match> summary = scoreboard.summary();

        assertThat(summary).hasSize(1);
        assertThat(summary.getFirst().id()).isEqualTo(started.id());
        assertThat(summary.getFirst().homeScore()).isEqualTo(2);
        assertThat(summary.getFirst().awayScore()).isEqualTo(1);
        assertThat(summary.getFirst().sequence()).isEqualTo(started.sequence());
    }

    @Test
    void finishMatchRemovesMatchAndReturnsFinalSnapshot() {
        Match started = scoreboard.startMatch("Argentina", "Brazil");
        scoreboard.updateScore(started.id(), 2, 1);

        Match finished = scoreboard.finishMatch(started.id());

        assertThat(finished.id()).isEqualTo(started.id());
        assertThat(finished.homeTeam()).isEqualTo("Argentina");
        assertThat(finished.awayTeam()).isEqualTo("Brazil");
        assertThat(finished.homeScore()).isEqualTo(2);
        assertThat(finished.awayScore()).isEqualTo(1);
        assertThat(finished.sequence()).isEqualTo(started.sequence());
        assertThat(scoreboard.summary()).isEmpty();
        assertThatThrownBy(() -> scoreboard.updateScore(started.id(), 3, 3))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void finishMatchWithUnknownIdThrowsIllegalStateException() {
        scoreboard.startMatch("Argentina", "Brazil");

        assertThatThrownBy(() -> scoreboard.finishMatch(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void finishMatchWithNullIdThrowsNullPointerException() {
        assertThatThrownBy(() -> scoreboard.finishMatch(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void finishMatchCannotBeFinishedTwice() {
        Match started = scoreboard.startMatch("Argentina", "Brazil");
        scoreboard.finishMatch(started.id());

        assertThatThrownBy(() -> scoreboard.finishMatch(started.id()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void summaryReturnsEmptyListWhenNoMatchesExist() {
        assertThat(scoreboard.summary()).isEmpty();
    }

    @Test
    void summaryOrdersByTotalScoreDescThenBySequenceDesc() {
        Match mexicoCanada = scoreboard.startMatch("Mexico", "Canada");
        Match spainBrazil = scoreboard.startMatch("Spain", "Brazil");
        Match germanyFrance = scoreboard.startMatch("Germany", "France");
        Match uruguayItaly = scoreboard.startMatch("Uruguay", "Italy");
        Match argentinaAustralia = scoreboard.startMatch("Argentina", "Australia");

        scoreboard.updateScore(mexicoCanada.id(), 0, 5);
        scoreboard.updateScore(spainBrazil.id(), 10, 2);
        scoreboard.updateScore(germanyFrance.id(), 2, 2);
        scoreboard.updateScore(uruguayItaly.id(), 6, 6);
        scoreboard.updateScore(argentinaAustralia.id(), 3, 1);

        assertThat(scoreboard.summary())
                .extracting(Match::homeTeam)
                .containsExactly("Uruguay", "Spain", "Mexico", "Argentina", "Germany");
    }

    @Test
    void summaryExcludesFinishedMatches() {
        Match live = scoreboard.startMatch("Argentina", "Brazil");
        Match finished = scoreboard.startMatch("Germany", "France");
        scoreboard.updateScore(finished.id(), 2, 2);
        scoreboard.finishMatch(finished.id());

        assertThat(scoreboard.summary())
                .extracting(Match::id)
                .containsExactly(live.id());
    }

    @Test
    void summaryOrdersMatchesWithIntegerMaxScoresWithoutOverflow() {
        Match highestTotal = scoreboard.startMatch("Argentina", "Brazil");
        Match middleTotal = scoreboard.startMatch("Germany", "France");
        Match lowestTotal = scoreboard.startMatch("Spain", "Italy");

        scoreboard.updateScore(highestTotal.id(), Integer.MAX_VALUE, Integer.MAX_VALUE);
        scoreboard.updateScore(middleTotal.id(), Integer.MAX_VALUE, 0);
        scoreboard.updateScore(lowestTotal.id(), 0, 0);

        List<Match> summary = scoreboard.summary();

        assertThat(summary).extracting(Match::id)
                .containsExactly(highestTotal.id(), middleTotal.id(), lowestTotal.id());
        assertThat((long) summary.getFirst().homeScore() + summary.getFirst().awayScore())
                .isEqualTo(2L * Integer.MAX_VALUE);
    }

    @Test
    void summaryReturnsImmutableSnapshot() {
        Match started = scoreboard.startMatch("Argentina", "Brazil");
        scoreboard.updateScore(started.id(), 1, 0);
        List<Match> snapshot = scoreboard.summary();

        assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.add(started)).isInstanceOf(UnsupportedOperationException.class);

        scoreboard.updateScore(started.id(), 3, 3);
        scoreboard.startMatch("Germany", "France");

        assertThat(snapshot).extracting(Match::homeScore).containsExactly(1);
    }

    @Test
    void getMatchReturnsStartedMatch() {
        Match started = scoreboard.startMatch("Argentina", "Brazil");

        assertThat(scoreboard.getMatch(started.id())).contains(started);
    }

    @Test
    void getMatchReflectsUpdatedScore() {
        Match started = scoreboard.startMatch("Argentina", "Brazil");
        scoreboard.updateScore(started.id(), 3, 1);

        Match found = scoreboard.getMatch(started.id()).orElseThrow();

        assertThat(found.homeScore()).isEqualTo(3);
        assertThat(found.awayScore()).isEqualTo(1);
        assertThat(found.id()).isEqualTo(started.id());
        assertThat(found.homeTeam()).isEqualTo("Argentina");
        assertThat(found.awayTeam()).isEqualTo("Brazil");
        assertThat(found.sequence()).isEqualTo(started.sequence());
    }

    @Test
    void getMatchWithUnknownIdReturnsEmpty() {
        scoreboard.startMatch("Argentina", "Brazil");

        assertThat(scoreboard.getMatch(UUID.randomUUID())).isEmpty();
    }

    @Test
    void getMatchOfFinishedMatchReturnsEmpty() {
        Match finished = scoreboard.startMatch("Argentina", "Brazil");
        Match live = scoreboard.startMatch("Germany", "France");
        scoreboard.finishMatch(finished.id());

        assertThat(scoreboard.getMatch(finished.id())).isEmpty();
        assertThat(scoreboard.getMatch(live.id())).contains(live);
    }

    @Test
    void getMatchResolvesSameFixtureInstancesIndependently() {
        Match first = scoreboard.startMatch("Argentina", "Brazil");
        Match second = scoreboard.startMatch("Argentina", "Brazil");
        scoreboard.updateScore(first.id(), 5, 0);
        scoreboard.updateScore(second.id(), 0, 1);

        Match firstFound = scoreboard.getMatch(first.id()).orElseThrow();
        Match secondFound = scoreboard.getMatch(second.id()).orElseThrow();

        assertThat(firstFound.homeScore()).isEqualTo(5);
        assertThat(firstFound.awayScore()).isZero();
        assertThat(secondFound.homeScore()).isZero();
        assertThat(secondFound.awayScore()).isEqualTo(1);
        assertThat(firstFound.sequence()).isNotEqualTo(secondFound.sequence());
    }

    @Test
    void getMatchWithNullIdThrowsNullPointerException() {
        Match started = scoreboard.startMatch("Argentina", "Brazil");

        assertThatThrownBy(() -> scoreboard.getMatch(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id");

        assertThat(scoreboard.getMatch(started.id())).contains(started);
    }

    @Test
    void getMatchReturnsSnapshotUnaffectedByLaterUpdates() {
        Match started = scoreboard.startMatch("Argentina", "Brazil");
        scoreboard.updateScore(started.id(), 1, 0);

        Match snapshot = scoreboard.getMatch(started.id()).orElseThrow();

        scoreboard.updateScore(started.id(), 3, 3);

        assertThat(snapshot.homeScore()).isEqualTo(1);
        assertThat(snapshot.awayScore()).isZero();
        assertThat(scoreboard.getMatch(started.id()).orElseThrow().homeScore()).isEqualTo(3);
    }

    @Test
    void getMatchDoesNotAffectSummary() {
        Match first = scoreboard.startMatch("Argentina", "Brazil");
        Match second = scoreboard.startMatch("Germany", "France");
        scoreboard.updateScore(second.id(), 2, 1);

        scoreboard.getMatch(first.id());
        scoreboard.getMatch(UUID.randomUUID());

        assertThat(scoreboard.summary()).extracting(Match::id).containsExactly(second.id(), first.id());
    }}