package com.master.matchmaking.model.simulation;

import com.master.matchmaking.model.entity.QueueRequestEntity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the quality metric calculations in {@link MatchResult}.
 */
class MatchResultTest {


    @Test
    void skillQuality_perfectMatch() {
        var m = match(player(500, "EU-WEST", 20), player(500, "EU-WEST", 20), 5, 5, 0, 20);
        assertEquals(100, m.skillQuality());
    }

    @Test
    void skillQuality_worstCase() {
        var m = match(player(0, "EU-WEST", 20), player(500, "EU-WEST", 20), 5, 5, 250, 20);
        assertEquals(0, m.skillQuality());
    }

    @Test
    void skillQuality_beyondThreshold_clampedToZero() {
        var m = match(player(0, "EU-WEST", 20), player(600, "EU-WEST", 20), 5, 5, 600, 20);
        assertEquals(0, m.skillQuality());
    }

    @Test
    void skillQuality_midRange() {
        // 125 / 250 = 0.5 → (1 - 0.5) * 100 = 50
        var m = match(player(100, "EU-WEST", 20), player(225, "EU-WEST", 20), 5, 5, 125, 20);
        assertEquals(50, m.skillQuality());
    }


    @Test
    void waitTimeQuality_instant() {
        var m = match(player(500, "EU-WEST", 20), player(500, "EU-WEST", 20), 0, 0, 0, 20);
        assertEquals(100, m.waitTimeQuality());
    }

    @Test
    void waitTimeQuality_sixtySeconds() {
        var m = match(player(500, "EU-WEST", 20), player(500, "EU-WEST", 20), 60, 60, 0, 20);
        assertEquals(0, m.waitTimeQuality());
    }

    @Test
    void waitTimeQuality_thirtySeconds() {
        // avg=30 → (1 - 30/60) * 100 = 50
        var m = match(player(500, "EU-WEST", 20), player(500, "EU-WEST", 20), 30, 30, 0, 20);
        assertEquals(50, m.waitTimeQuality());
    }


    @Test
    void latencyQuality_sameRegionLowLatency() {
        var p1 = player(500, "EU-WEST", 15);
        var p2 = player(500, "EU-WEST", 20);
        // avg base = 17, estimatedLatency = 17 (same region, penalty 0)
        var m = new MatchResult(p1, p2, 0, 5, 5, 0, 17);
        // regionScore=70, maxLatency=20 <=30 → latencyScore=30 → total=100
        assertEquals(100, m.latencyQuality());
    }

    @Test
    void latencyQuality_crossContinent() {
        var p1 = player(500, "EU-WEST", 20);
        var p2 = player(500, "NA-EAST", 25);
        // avg base = 22, penalty ~25 → estimatedLatency = 47
        var m = new MatchResult(p1, p2, 0, 5, 5, 0, 47);
        // penalty = 47 - 22 = 25 → regionScore=20
        // maxLatency=25 <=30 → latencyScore=30 → total=50
        assertEquals(50, m.latencyQuality());
    }


    @Test
    void matchQuality_isAverageOfThreeComponents() {
        var p1 = player(500, "EU-WEST", 15);
        var p2 = player(500, "EU-WEST", 20);
        var m = new MatchResult(p1, p2, 0, 0, 0, 0, 17);
        int expected = (m.skillQuality() + m.latencyQuality() + m.waitTimeQuality()) / 3;
        assertEquals(expected, m.matchQuality());
    }


    @Test
    void averageWaitTime_calculatedCorrectly() {
        var m = match(player(500, "EU-WEST", 20), player(500, "EU-WEST", 20), 10, 30, 0, 20);
        assertEquals(20.0, m.averageWaitTime(), 0.001);
    }

    private static QueueRequestEntity player(int skill, String region, int latencyMs) {
        return QueueRequestEntity.builder()
                .id(UUID.randomUUID())
                .skillRating(skill)
                .region(region)
                .latencyMs(latencyMs)
                .joinTimeSeconds(0)
                .patienceSeconds(120)
                .build();
    }

    private static MatchResult match(QueueRequestEntity p1, QueueRequestEntity p2,
                                     int waitP1, int waitP2, int skillDiff, int latency) {
        return new MatchResult(p1, p2, 0, waitP1, waitP2, skillDiff, latency);
    }
}