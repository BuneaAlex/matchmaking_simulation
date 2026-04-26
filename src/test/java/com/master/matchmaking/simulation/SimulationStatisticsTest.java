package com.master.matchmaking.simulation;

import com.master.matchmaking.model.entity.QueueRequestEntity;
import com.master.matchmaking.model.simulation.MatchResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationStatisticsTest {

    @Test void totalMatches() {
        assertEquals(2, new SimulationStatistics(List.of(match(5,5,10,20), match(10,10,20,30)), List.of(), 10).totalMatches());
    }

    @Test void abandonRate() {
        assertEquals(25.0, new SimulationStatistics(List.of(), List.of(player(100,"EU-WEST",20)), 4).abandonRate(), 0.01);
    }

    @Test void abandonRate_zeroPlayers() {
        assertEquals(0, new SimulationStatistics(List.of(), List.of(), 0).abandonRate());
    }

    @Test void averageWaitTime() {
        assertEquals(10.0, new SimulationStatistics(List.of(match(10,20,0,20), match(4,6,0,20)), List.of(), 4).averageWaitTime(), 0.01);
    }

    @Test void averageSkillDiff() {
        assertEquals(75.0, new SimulationStatistics(List.of(match(5,5,50,20), match(5,5,100,20)), List.of(), 4).averageSkillDiff(), 0.01);
    }

    @Test void waitTimeDistribution_buckets() {
        var stats = new SimulationStatistics(List.of(match(2,2,0,20), match(10,10,0,20), match(40,40,0,20), match(70,70,0,20)), List.of(), 8);
        Map<String,Integer> d = stats.waitTimeDistribution();
        assertEquals(1, d.get("0-5 s")); assertEquals(1, d.get("6-30 s"));
        assertEquals(1, d.get("31-59 s")); assertEquals(1, d.get(">60 s"));
    }

    @Test void skillDiffDistribution_buckets() {
        var stats = new SimulationStatistics(List.of(match(5,5,10,20), match(5,5,50,20), match(5,5,100,20), match(5,5,200,20), match(5,5,300,20)), List.of(), 10);
        Map<String,Integer> d = stats.skillDiffDistribution();
        assertEquals(1, d.get("0-25")); assertEquals(1, d.get("26-75")); assertEquals(1, d.get("76-150"));
        assertEquals(1, d.get("151-250")); assertEquals(1, d.get(">250"));
    }

    @Test void latencyDistribution_buckets() {
        var stats = new SimulationStatistics(List.of(match(5,5,0,20), match(5,5,0,50), match(5,5,0,80), match(5,5,0,150), match(5,5,0,200)), List.of(), 10);
        Map<String,Integer> d = stats.latencyDistribution();
        assertEquals(1, d.get("10-30 ms")); assertEquals(1, d.get("31-60 ms")); assertEquals(1, d.get("61-100 ms"));
        assertEquals(1, d.get("101-180 ms")); assertEquals(1, d.get(">180 ms"));
    }

    private static QueueRequestEntity player(int skill, String region, int latencyMs) {
        return QueueRequestEntity.builder().id(UUID.randomUUID()).skillRating(skill)
                .region(region).latencyMs(latencyMs).joinTimeSeconds(0).patienceSeconds(120).build();
    }

    private static MatchResult match(int waitP1, int waitP2, int skillDiff, int latency) {
        return new MatchResult(player(500, "EU-WEST", 20), player(500 + skillDiff, "EU-WEST", 20),
                0, waitP1, waitP2, skillDiff, latency);
    }
}