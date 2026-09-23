package com.andrewsavich.scoreboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchTest {

    @Test
    void validMatchExposesAllComponents() {
        UUID id = UUID.randomUUID();

        Match match = new Match(id, "Argentina", "Brazil", 2, 1, 7L);

        assertThat(match.id()).isEqualTo(id);
        assertThat(match.homeTeam()).isEqualTo("Argentina");
        assertThat(match.awayTeam()).isEqualTo("Brazil");
        assertThat(match.homeScore()).isEqualTo(2);
        assertThat(match.awayScore()).isEqualTo(1);
        assertThat(match.sequence()).isEqualTo(7L);
    }

    @Test
    void nullIdIsRejected() {
        assertThatThrownBy(() -> new Match(null, "Argentina", "Brazil", 0, 0, 1L))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void invalidHomeTeamIsRejected(String homeTeam) {
        assertThatThrownBy(() -> new Match(UUID.randomUUID(), homeTeam, "Brazil", 0, 0, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void invalidAwayTeamIsRejected(String awayTeam) {
        assertThatThrownBy(() -> new Match(UUID.randomUUID(), "Argentina", awayTeam, 0, 0, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void identicalTeamsAreRejected() {
        assertThatThrownBy(() -> new Match(UUID.randomUUID(), "Brazil", "Brazil", 0, 0, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeHomeScoreIsRejected() {
        assertThatThrownBy(() -> new Match(UUID.randomUUID(), "Argentina", "Brazil", -1, 0, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeAwayScoreIsRejected() {
        assertThatThrownBy(() -> new Match(UUID.randomUUID(), "Argentina", "Brazil", 0, -1, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}