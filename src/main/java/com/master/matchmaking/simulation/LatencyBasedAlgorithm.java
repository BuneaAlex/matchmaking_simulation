package com.master.matchmaking.simulation;

import com.master.matchmaking.model.simulation.AlgorithmWeights;
import com.master.matchmaking.model.simulation.MatchResult;
import com.master.matchmaking.model.simulation.QueueEntry;

import java.util.*;

/**
 * Latency-Based Matchmaking.
 * <p>
 * <b>Strategy:</b> the latency weight controls <b>how long the algorithm holds
 * players at each geographic level</b> before expanding scope:
 * <ol>
 *   <li><b>Phase 1 – Same sub-region</b> (e.g. EU-WEST ↔ EU-WEST, penalty 0 ms):
 *       All players start here. Only players who have waited at least
 *       {@code subRegionHoldSeconds} without finding a match move on.</li>
 *   <li><b>Phase 2 – Same continent</b> (e.g. EU-WEST ↔ EU-NORTH, penalty 5–10 ms):
 *       Players who couldn't match in Phase 1. Only those who have waited at
 *       least {@code continentHoldSeconds} move on.</li>
 *   <li><b>Phase 3 – Cross-continent</b> (e.g. EU ↔ NA, penalty 25–35 ms):
 *       Last resort for players who exhausted both regional pools.</li>
 * </ol>
 * Higher latency weight → longer hold times → players wait longer for a
 * same-sub-region match before expanding → more matches at the tightest
 * geographic level → higher latency quality score.
 * <p>
 * Hold times scale with the latency weight using a steeper curve:
 * <ul>
 *   <li>80% → sub-region hold 40 s, continent hold 60 s</li>
 *   <li>70% → sub-region hold 25 s, continent hold 40 s</li>
 *   <li>60% → sub-region hold 10 s, continent hold 20 s</li>
 *   <li>50% → sub-region hold 0 s (no hold), continent hold 5 s</li>
 * </ul>
 */
public class LatencyBasedAlgorithm implements MatchmakingAlgorithm {
    private final AlgorithmWeights weights;

    /**
     * Seconds a player must wait before leaving sub-region pool for continent pool.
     */
    private final int subRegionHoldSeconds;

    /**
     * Seconds a player must wait before leaving continent pool for cross-continent pool.
     */
    private final int continentHoldSeconds;

    public LatencyBasedAlgorithm(AlgorithmWeights weights) {
        this.weights = weights;
        // Steep curve: 50% → 0s, 60% → 10s, 70% → 25s, 80% → 40s
        // Formula: (weight - 0.5) * 133, clamped to [0, ∞)
        this.subRegionHoldSeconds = Math.max(0, (int) ((weights.weightLatency() - 0.5) * 133));
        // Continent hold: 50% → 5s, 60% → 20s, 70% → 40s, 80% → 60s
        this.continentHoldSeconds = Math.max(5, (int) ((weights.weightLatency() - 0.5) * 200));
    }

    @Override
    public String name() {
        return "Latency-Based";
    }

    @Override
    public AlgorithmWeights weights() {
        return weights;
    }

    @Override
    public List<MatchResult> findMatches(List<QueueEntry> queue, int currentSecond) {
        List<MatchResult> matches = new ArrayList<>();

        // ── Phase 1: same sub-region ──────────────────────────────────────
        Map<String, List<QueueEntry>> subRegionBuckets = new HashMap<>();
        for (QueueEntry e : queue) {
            subRegionBuckets.computeIfAbsent(e.getQueueRequest().getRegion(), k -> new ArrayList<>()).add(e);
        }

        List<QueueEntry> phase2Pool = new ArrayList<>();
        match(subRegionBuckets, currentSecond, matches, subRegionHoldSeconds, phase2Pool);

        // ── Phase 2: same continent ───────────────────────────────────────
        Map<String, List<QueueEntry>> continentBuckets = new HashMap<>();
        for (QueueEntry e : phase2Pool) {
            String continent = getContinent(e.getQueueRequest().getRegion());
            continentBuckets.computeIfAbsent(continent, k -> new ArrayList<>()).add(e);
        }

        List<QueueEntry> phase3Pool = new ArrayList<>();
        match(continentBuckets, currentSecond, matches, continentHoldSeconds, phase3Pool);

        // ── Phase 3: cross-continent ──────────────────────────────────────
        matchBucket(phase3Pool, currentSecond, matches);

        // ── Rebuild queue: only unmatched players remain ──────────────────
        // Collect all players that were matched in any phase
        Set<UUID> matchedIds = new HashSet<>();
        for (MatchResult m : matches) {
            matchedIds.add(m.queueRequest1().getId());
            matchedIds.add(m.queueRequest2().getId());
        }
        queue.removeIf(e -> matchedIds.contains(e.getQueueRequest().getId()));

        return matches;
    }


    private void match(Map<String, List<QueueEntry>> bucketCategory, int currentSecond, List<MatchResult> matches,
                       int subRegionHoldSeconds, List<QueueEntry> phasePool) {
        for (List<QueueEntry> bucket : bucketCategory.values()) {
            // Try to match everyone in this bucket category
            matchBucket(bucket, currentSecond, matches);
            // Unmatched: those who have waited long enough move to the next phase,
            // others stay in queue for next tick
            for (QueueEntry e : bucket) {
                if (e.waitTimeAt(currentSecond) >= subRegionHoldSeconds) {
                    phasePool.add(e);
                }
            }
        }
    }

    /**
     * Match players within a bucket. Sorts by base latency, pairs best matches.
     * Matched entries are removed from the list; unmatched remain.
     */
    private void matchBucket(List<QueueEntry> bucket, int currentSecond, List<MatchResult> matches) {
        bucket.sort(Comparator.comparingInt((QueueEntry e) -> e.getQueueRequest().getLatencyMs()));

        boolean[] matched = new boolean[bucket.size()];

        for (int i = 0; i < bucket.size(); i++) {
            if (matched[i]) continue;
            QueueEntry a = bucket.get(i);

            int waitA = a.waitTimeAt(currentSecond);
            int latTolA = latencyTolerance(waitA);
            int skillTolA = skillTolerance(waitA);

            int bestIdx = -1;
            int bestScore = Integer.MAX_VALUE;

            for (int j = i + 1; j < bucket.size(); j++) {
                if (matched[j]) continue;
                QueueEntry b = bucket.get(j);

                int waitB = b.waitTimeAt(currentSecond);
                int effectiveLatTol = Math.max(latTolA, latencyTolerance(waitB));

                int latency = estimateLatency(a, b);
                if (latency > effectiveLatTol) continue;

                int skillDiff = Math.abs(a.getQueueRequest().getSkillRating() - b.getQueueRequest().getSkillRating());
                int effectiveSkillTol = Math.max(skillTolA, skillTolerance(waitB));
                int skillPenalty = skillDiff > effectiveSkillTol ? 5000 : skillDiff;

                // Score: latency primary, skill as tiebreaker
                int score = latency * 10 + skillPenalty;

                if (score < bestScore) {
                    bestScore = score;
                    bestIdx = j;
                }
            }

            if (bestIdx != -1) {
                matched[i] = true;
                matched[bestIdx] = true;
                matches.add(buildMatch(a, bucket.get(bestIdx), currentSecond));
            }
        }

        for (int i = bucket.size() - 1; i >= 0; i--) {
            if (matched[i]) bucket.remove(i);
        }
    }


    private String getContinent(String region) {
        return region.substring(0, region.indexOf('-'));
    }
}

