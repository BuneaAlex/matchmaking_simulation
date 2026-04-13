package com.master.matchmaking.simulation;

import com.master.matchmaking.model.simulation.AlgorithmWeights;
import com.master.matchmaking.model.simulation.MatchResult;
import com.master.matchmaking.model.simulation.QueueEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Skill-Based Matchmaking (SBMM) — <b>Bracket-and-Rank</b> strategy.
 * <p>
 * Modelled after real-world ranked matchmaking (ELO tiers, MMR bands).
 * This is a <em>ranked mode</em> algorithm: match quality takes strict
 * priority over speed. Players with vastly different skill ratings must
 * <b>never</b> be matched — they wait longer instead.
 * <ol>
 *   <li><b>Skill gate:</b> players are sorted by skill. A player can only
 *       match with another whose skill diff is within a tight search range
 *       controlled by the skill weight. A hard cap of {@value #MAX_SKILL_DIFF}
 *       ensures no match ever crosses 2+ divisions, expanding slowly
 *       to {@value #ABSOLUTE_SKILL_CAP} only for very long waiters.</li>
 *   <li><b>Latency gate:</b> a latency ceiling filters out high-latency
 *       pairs. Higher latency weight → tighter ceiling → same-region forced.</li>
 *   <li><b>Rank:</b> all pairs passing both gates are scored with a weighted
 *       formula and the globally best pair is matched first.</li>
 * </ol>
 * <p>
 * <h3>How the weights control behaviour</h3>
 * <ul>
 *   <li><b>Skill weight</b> (50–80 %) controls the search range.
 *       Higher → narrower range → better skill matches, longer waits.</li>
 *   <li><b>Latency weight</b> (10–30 %) controls the latency ceiling.
 *       At 30 % only low-latency (same-region) pairs pass; at 10 % cross-region
 *       pairs are freely allowed.</li>
 *   <li><b>Wait-time weight</b> (10–30 %) adds urgency for long-waiters.</li>
 * </ul>
 * <p>
 * <h3>Search range (ranked-mode skill brackets)</h3>
 * <pre>
 *   searchRange = TIGHT_RANGE + (LOOSE_RANGE − TIGHT_RANGE)
 *               × (MAX_SKILL_WEIGHT − skillWeight) / (MAX_SKILL_WEIGHT − MIN_SKILL_WEIGHT)
 * </pre>
 * <ul>
 *   <li>Skill 80 % → search range  75 pts  (same division only)</li>
 *   <li>Skill 70 % → search range 100 pts  (adjacent division)</li>
 *   <li>Skill 60 % → search range 125 pts  (1–2 divisions)</li>
 *   <li>Skill 50 % → search range 150 pts  (max — still within "decent")</li>
 * </ul>
 * <p>
 * <h3>Hard skill cap</h3>
 * No match may ever exceed {@value #MAX_SKILL_DIFF} skill diff at normal wait
 * times. For players waiting longer than 30 s the cap expands slowly toward
 * {@value #ABSOLUTE_SKILL_CAP} to prevent permanent starvation:
 * <pre>
 *   skillCap(waitSec) = MAX_SKILL_DIFF
 *                     + (ABSOLUTE_CAP − MAX_SKILL_DIFF) × (1 − e^(−waitSec / 120))
 * </pre>
 * <p>
 * <h3>Latency ceiling</h3>
 * A tight, gaming-realistic ceiling derived linearly from the latency weight:
 * <ul>
 *   <li>Latency 10 % → ceiling 150 ms  (allows cross-region freely)</li>
 *   <li>Latency 15 % → ceiling 125 ms</li>
 *   <li>Latency 20 % → ceiling 100 ms  (prefers same-continent)</li>
 *   <li>Latency 30 % → ceiling  50 ms  (forces same-region)</li>
 * </ul>
 * <p>
 * <h3>Within-bracket scoring</h3>
 * <pre>
 *   score = wSkill   × (skillDiff / searchRange)
 *         + wLatency × (latency   / 300)
 *         − wWait    × ((waitA + waitB) / 120)
 * </pre>
 */
public class SkillBasedAlgorithm implements MatchmakingAlgorithm {
    private final AlgorithmWeights weights;

    /**
     * Computed search range (rating points) — the primary skill window.
     * Controlled by skill weight; ranges from {@value #TIGHT_RANGE} to
     * {@value #LOOSE_RANGE}.
     */
    private final int searchRange;

    // ── Ranked-mode skill constants ───────────────────────────────────────

    /**
     * Tightest search range (at max skill weight 80 %). Same division.
     */
    private static final int TIGHT_RANGE = 75;

    /**
     * Loosest search range (at min skill weight 50 %). Still "decent".
     */
    private static final int LOOSE_RANGE = 150;

    /**
     * Hard skill-diff cap — no match exceeds this under normal conditions.
     */
    private static final int MAX_SKILL_DIFF = 150;

    /**
     * Absolute skill-diff cap — the ceiling for very long waiters (30 s+).
     * Allows up to "poor" (151–250) but never "bad" (>250).
     */
    private static final int ABSOLUTE_SKILL_CAP = 200;

    /**
     * Decay rate for the skill-cap expansion (seconds).
     */
    private static final double SKILL_CAP_DECAY = 120.0;

    /**
     * Minimum skill weight for this algorithm.
     */
    private static final double MIN_SKILL_WEIGHT = 0.50;

    /**
     * Maximum skill weight for this algorithm.
     */
    private static final double MAX_SKILL_WEIGHT = 0.80;

    // ── Latency ceiling constants ─────────────────────────────────────────

    /**
     * Base latency ceiling at wait-time 0 (derived from latency weight).
     */
    private final double baseLatCeiling;

    /**
     * Most lenient latency ceiling (ms) — at minimum latency weight.
     */
    private static final double MAX_LAT_CEILING = 150.0;

    /**
     * Tightest latency ceiling (ms) — at maximum latency weight.
     */
    private static final double MIN_LAT_CEILING = 50.0;

    /**
     * Maximum latency weight for this algorithm (from AlgorithmType).
     */
    private static final double MAX_LAT_WEIGHT = 0.30;

    /**
     * Minimum latency weight for this algorithm.
     */
    private static final double MIN_LAT_WEIGHT = 0.10;

    /**
     * Decay rate for the latency ceiling expansion (seconds).
     */
    private static final double LAT_CEILING_DECAY = 120.0;

    public SkillBasedAlgorithm(AlgorithmWeights weights) {
        this.weights = weights;

        // Search range: linear from TIGHT_RANGE (at 80%) to LOOSE_RANGE (at 50%)
        double t = (MAX_SKILL_WEIGHT - weights.weightSkill()) / (MAX_SKILL_WEIGHT - MIN_SKILL_WEIGHT);
        t = Math.max(0.0, Math.min(1.0, t));
        this.searchRange = TIGHT_RANGE + (int) (t * (LOOSE_RANGE - TIGHT_RANGE));

        // Latency ceiling: linear from MAX_LAT_CEILING (at 10%) to MIN_LAT_CEILING (at 30%)
        double lt = (weights.weightLatency() - MIN_LAT_WEIGHT) / (MAX_LAT_WEIGHT - MIN_LAT_WEIGHT);
        lt = Math.max(0.0, Math.min(1.0, lt));
        this.baseLatCeiling = MAX_LAT_CEILING - lt * (MAX_LAT_CEILING - MIN_LAT_CEILING);
    }

    @Override
    public String name() {
        return "Skill-Based (SBMM)";
    }

    @Override
    public AlgorithmWeights weights() {
        return weights;
    }

    @Override
    public List<MatchResult> findMatches(List<QueueEntry> queue, int currentSecond) {
        List<MatchResult> matches = new ArrayList<>();
        if (queue.size() < 2) return matches;

        // ── 1. Sort by skill ──────────────────────────────────────────────
        queue.sort(Comparator.comparingInt(e -> e.getQueueRequest().getSkillRating()));

        // ── 2. Build scored pairs within search range ─────────────────────
        List<ScoredPair> scoredPairs = new ArrayList<>();

        for (int i = 0; i < queue.size(); i++) {
            QueueEntry a = queue.get(i);
            int skillA = a.getQueueRequest().getSkillRating();
            int waitA = a.waitTimeAt(currentSecond);

            // Hard skill cap for player A (expands slowly for long waiters)
            int skillCapA = skillCap(waitA);

            for (int j = i + 1; j < queue.size(); j++) {
                QueueEntry b = queue.get(j);
                int skillDiff = b.getQueueRequest().getSkillRating() - skillA;

                // Effective skill gate = min of both players' caps,
                // but never wider than the search range
                int waitB = b.waitTimeAt(currentSecond);
                int effectiveSkillMax = Math.min(searchRange, Math.min(skillCapA, skillCap(waitB)));

                if (skillDiff > effectiveSkillMax) break;

                int latency = estimateLatency(a, b);

                // ── Latency ceiling (expanding with wait time) ──────────────
                // Use the wider of the two so that long-waiters can escape
                // but fresh players still benefit from the tight ceiling.
                int effectiveLatCeiling = Math.max(latencyCeiling(waitA), latencyCeiling(waitB));

                if (latency > effectiveLatCeiling) continue;

                // ── Weighted score (within-bracket normalization) ───────────
                double skillNorm = skillDiff / (double) searchRange;
                double latNorm = latency / (double) MAX_LATENCY_RANGE;
                double waitUrgency = (waitA + waitB) / 120.0;

                double score = weights.weightSkill() * skillNorm
                        + weights.weightLatency() * latNorm
                        - weights.weightWaitTime() * waitUrgency;

                scoredPairs.add(new ScoredPair(i, j, score));
            }
        }

        // ── 3. Greedy global assignment — best (lowest) score first ───────
        scoredPairs.sort(Comparator.comparingDouble(ScoredPair::score));

        boolean[] matched = new boolean[queue.size()];

        for (ScoredPair pair : scoredPairs) {
            if (matched[pair.i] || matched[pair.j]) continue;
            matched[pair.i] = true;
            matched[pair.j] = true;
            matches.add(buildMatch(queue.get(pair.i), queue.get(pair.j), currentSecond));
        }

        // Remove matched entries backwards to preserve indices
        for (int i = queue.size() - 1; i >= 0; i--) {
            if (matched[i]) queue.remove(i);
        }

        return matches;
    }

    // ── Skill cap ─────────────────────────────────────────────────────

    /**
     * Hard skill-diff ceiling for a player who has waited {@code waitSec}.
     * <p>
     * Starts at {@value #MAX_SKILL_DIFF} (the "decent" boundary) and
     * asymptotically approaches {@value #ABSOLUTE_SKILL_CAP} for very long
     * waiters. This prevents extreme mismatches under normal conditions
     * while allowing slightly wider matches to clear stuck players.
     * <pre>
     *   cap = MAX_SKILL_DIFF + (ABSOLUTE_CAP − MAX_SKILL_DIFF) × (1 − e^(−waitSec / SKILL_CAP_DECAY))
     * </pre>
     *
     * @return maximum acceptable skill diff for this player
     */
    private static int skillCap(int waitSec) {
        double extra = (ABSOLUTE_SKILL_CAP - MAX_SKILL_DIFF)
                * (1.0 - Math.exp(-waitSec / SKILL_CAP_DECAY));
        return (int) (MAX_SKILL_DIFF + extra);
    }

    // ── Latency ceiling ──────────────────────────────────────────────────

    /**
     * Expanding latency ceiling for a player who has waited {@code waitSec}.
     * <p>
     * Starts at {@link #baseLatCeiling} (controlled by latency weight) and
     * asymptotically approaches {@link #MAX_LATENCY_RANGE} as the player
     * waits longer. This ensures long-waiters eventually find a match even
     * across regions.
     *
     * @return maximum acceptable estimated latency (ms) for this player
     */
    private int latencyCeiling(int waitSec) {
        double remaining = MAX_LATENCY_RANGE - baseLatCeiling;
        double expansion = remaining * (1.0 - Math.exp(-waitSec / LAT_CEILING_DECAY));
        return (int) (baseLatCeiling + expansion);
    }

    // ── Inner record ─────────────────────────────────────────────────────

    private record ScoredPair(int i, int j, double score) {
    }
}

