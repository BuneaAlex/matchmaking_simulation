package com.master.matchmaking.simulation;

import com.master.matchmaking.model.entity.QueueRequestEntity;
import com.master.matchmaking.model.simulation.AlgorithmWeights;
import com.master.matchmaking.model.simulation.MatchResult;
import com.master.matchmaking.model.simulation.QueueEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the four matchmaking algorithms.
 * Each test uses a small, hand-crafted queue to verify the algorithm's core behaviour.
 */
class MatchmakingAlgorithmTest {

    @Test
    void skillBased_matchesSimilarSkillPlayers() {
        var algo = new SkillBasedAlgorithm(new AlgorithmWeights(0.70, 0.15, 0.15));
        var q = queue(
                entry(500, "EU-WEST", 20, 0),
                entry(520, "EU-WEST", 25, 0),
                entry(900, "EU-WEST", 20, 0)  // far away in skill
        );
        List<MatchResult> matches = algo.findMatches(q, 10);
        assertEquals(1, matches.size());
        assertEquals(20, matches.get(0).skillDifference());
        assertEquals(1, q.size()); // 900-rated player left in queue
    }

    @Test
    void skillBased_rejectsLargeSkillGap() {
        var algo = new SkillBasedAlgorithm(new AlgorithmWeights(0.80, 0.10, 0.10));
        var q = queue(
                entry(100, "EU-WEST", 20, 0),
                entry(800, "EU-WEST", 20, 0)
        );
        // At t=5, skill diff=700 far exceeds any search range
        List<MatchResult> matches = algo.findMatches(q, 5);
        assertEquals(0, matches.size());
        assertEquals(2, q.size());
    }

    @Test
    void skillBased_emptyQueueReturnsNoMatches() {
        var algo = new SkillBasedAlgorithm(new AlgorithmWeights(0.70, 0.15, 0.15));
        List<MatchResult> matches = algo.findMatches(new ArrayList<>(), 10);
        assertTrue(matches.isEmpty());
    }


    @Test
    void latencyBased_prefersSameRegion() {
        var algo = new LatencyBasedAlgorithm(new AlgorithmWeights(0.10, 0.80, 0.10));
        var q = queue(
                entry(500, "EU-WEST", 20, 0),
                entry(510, "EU-WEST", 25, 0),
                entry(505, "NA-EAST", 30, 0)
        );
        // Phase 1: same sub-region → EU-WEST pair should match first
        List<MatchResult> matches = algo.findMatches(q, 10);
        assertFalse(matches.isEmpty());
        // The first match should be same-region (EU-WEST pair)
        MatchResult first = matches.get(0);
        assertEquals(first.queueRequest1().getRegion(), first.queueRequest2().getRegion());
    }

    @Test
    void latencyBased_matchesSameRegionPair() {
        var algo = new LatencyBasedAlgorithm(new AlgorithmWeights(0.15, 0.70, 0.15));
        var q = queue(
                entry(400, "EU-WEST", 15, 0),
                entry(600, "EU-WEST", 20, 0)
        );
        List<MatchResult> matches = algo.findMatches(q, 10);
        assertEquals(1, matches.size());
        assertTrue(q.isEmpty());
    }


    @Test
    void shortWaitTime_matchesAfterHoldTime() {
        // High wait weight → very short hold → matches quickly
        var algo = new ShortWaitTimeAlgorithm(new AlgorithmWeights(0.10, 0.10, 0.80));
        var q = queue(
                entry(500, "EU-WEST", 20, 0),
                entry(520, "EU-WEST", 25, 0)
        );
        // At t=5 (past hold of ~0s for 80% wait weight), should match
        List<MatchResult> matches = algo.findMatches(q, 5);
        assertEquals(1, matches.size());
    }

    @Test
    void shortWaitTime_doesNotMatchDuringHold() {
        // Low wait weight → longer hold (~4s)
        var algo = new ShortWaitTimeAlgorithm(new AlgorithmWeights(0.25, 0.25, 0.50));
        var q = queue(
                entry(500, "EU-WEST", 20, 0),
                entry(520, "EU-WEST", 25, 0)
        );
        // At t=1, players joined at t=0, wait=1s, hold ~4s → not eligible yet
        List<MatchResult> matches = algo.findMatches(q, 1);
        assertEquals(0, matches.size());
        assertEquals(2, q.size());
    }

    @Test
    void shortWaitTime_tickIntervalIsOne() {
        var algo = new ShortWaitTimeAlgorithm(new AlgorithmWeights(0.10, 0.10, 0.80));
        assertEquals(1, algo.tickIntervalSeconds());
    }


    @Test
    void crossRegionPenalty_sameRegion() {
        var algo = new SkillBasedAlgorithm(new AlgorithmWeights(0.70, 0.15, 0.15));
        assertEquals(0, algo.crossRegionPenalty("EU-WEST", "EU-WEST"));
    }

    @Test
    void crossRegionPenalty_sameContinent() {
        var algo = new SkillBasedAlgorithm(new AlgorithmWeights(0.70, 0.15, 0.15));
        assertEquals(5, algo.crossRegionPenalty("EU-WEST", "EU-NORTH"));
    }

    @Test
    void crossRegionPenalty_crossContinent() {
        var algo = new SkillBasedAlgorithm(new AlgorithmWeights(0.70, 0.15, 0.15));
        assertEquals(25, algo.crossRegionPenalty("EU-WEST", "NA-EAST"));
    }

    private static QueueRequestEntity player(int skill, String region, int latencyMs, int joinSec) {
        return QueueRequestEntity.builder()
                .id(UUID.randomUUID())
                .skillRating(skill)
                .region(region)
                .latencyMs(latencyMs)
                .joinTimeSeconds(joinSec)
                .patienceSeconds(300)
                .build();
    }

    private static QueueEntry entry(int skill, String region, int latencyMs, int joinSec) {
        return new QueueEntry(player(skill, region, latencyMs, joinSec), joinSec);
    }

    private static ArrayList<QueueEntry> queue(QueueEntry... entries) {
        return new ArrayList<>(List.of(entries));
    }
}

