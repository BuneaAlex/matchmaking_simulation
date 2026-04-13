package com.master.matchmaking.simulation;


import com.master.matchmaking.model.simulation.AlgorithmWeights;
import com.master.matchmaking.model.simulation.MatchResult;
import com.master.matchmaking.model.simulation.QueueEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Short Wait-Time Matchmaking — <b>Hold-and-Pick</b> strategy with tolerance gates.
 * <p>
 * <b>Core idea:</b> every player who enters the queue must wait a minimum
 * <em>hold time</em> before they become eligible for matching. During the hold
 * period the algorithm collects candidates; after the hold it picks the
 * globally best pair from all eligible players that pass through tolerance gates.
 * <p>
 * <h3>How the weights control behaviour</h3>
 * <ul>
 *   <li><b>Wait-time weight controls the hold duration.</b>
 *       High wait weight (80 %) → very short hold (≈ 0 s) → instant matches, low quality.
 *       Low wait weight (50 %) → longer hold (≈ 4 s) → more candidates → better quality.</li>
 *   <li><b>Skill and latency weights control both tolerance gates and scoring emphasis.</b>
 *       Among eligible candidates, only those within the expanding tolerance window
 *       (based on both players' wait times) are considered. Higher skill weight → tighter
 *       skill gate → algorithm rejects poor-skill matches rather than just down-ranking them.</li>
 * </ul>
 * <p>
 * <h3>Hold time formula</h3>
 * The hold uses a power curve ({@code p^0.6}) to spread out values at the
 * high-wait-weight end where linear interpolation compresses them:
 * <pre>
 *   p           = (1 − waitTimeWeight) / (1 − MIN_WAIT_WEIGHT)
 *   holdSeconds = MAX_HOLD × p^0.6
 * </pre>
 * Where {@code MAX_HOLD = 4 s} and {@code MIN_WAIT_WEIGHT = 0.50}.
 * <ul>
 *   <li>Wait 50 % → p=1.00  → hold 4.0 s (maximum quality accumulation)</li>
 *   <li>Wait 60 % → p=0.67  → hold 3.1 s</li>
 *   <li>Wait 70 % → p=0.40  → hold 2.2 s</li>
 *   <li>Wait 80 % → p=0.00  → hold 0.0 s (instant matching)</li>
 * </ul>
 * <p>
 * <h3>Scoring formula</h3>
 * <pre>
 *   score = weightSkill   × (skillDiff / 1000)
 *         + weightLatency × (latency / 300)
 *         − weightWait    × (avgWait / 60)
 * </pre>
 */
public class ShortWaitTimeAlgorithm implements MatchmakingAlgorithm {
    private final AlgorithmWeights weights;

    /**
     * Maximum hold time in seconds.
     * Applied when wait-time weight is at its minimum (50 %).
     * At max wait-time weight (80 %), the hold is 0 s.
     */
    private static final double MAX_HOLD_SECONDS = 4.0;

    /**
     * Minimum allowed wait-time weight for this algorithm (from AlgorithmType).
     * Used to normalize the hold-time formula.
     */
    private static final double MIN_WAIT_WEIGHT = 0.50;

    /**
     * Faster decay for this algorithm's tolerance gates (seconds).
     * The shared default is 60 s; here we use 30 s so that gates open
     * faster and players are not stuck waiting for candidates.
     */
    private static final double SWT_DECAY_RATE = 30.0;

    /**
     * Pre-computed hold time in seconds (fractional to preserve sensitivity
     * between close weight values such as 70 % and 80 %).
     * Players must have waited at least this long before they become eligible.
     */
    private final double holdSeconds;

    public ShortWaitTimeAlgorithm(AlgorithmWeights weights) {
        this.weights = weights;

        // Power-curve interpolation: wait=50% → MAX_HOLD, wait=80% → 0
        // Using p^0.6 spreads out the high-wait-weight end:
        //   wait 50 % → 4.0 s    wait 60 % → 3.1 s
        //   wait 70 % → 2.2 s    wait 80 % → 0.0 s
        // Compared to linear (4.0, 2.7, 1.3, 0.0), the 70 %–80 % gap grows
        // from 1.3 s to 2.2 s while the 50 %–60 % gap shrinks slightly.
        double p = (1.0 - weights.weightWaitTime()) / (1.0 - MIN_WAIT_WEIGHT);
        this.holdSeconds = MAX_HOLD_SECONDS * Math.pow(p, 0.6);
    }

    @Override
    public String name() {
        return "Short Wait-Time";
    }

    @Override
    public AlgorithmWeights weights() {
        return weights;
    }

    /**
     * 1-second ticks to preserve fine-grained wait-time resolution.
     */
    @Override
    public int tickIntervalSeconds() {
        return 1;
    }

    @Override
    public List<MatchResult> findMatches(List<QueueEntry> queue, int currentSecond) {
        List<MatchResult> matches = new ArrayList<>();

        // ── Partition queue into eligible (past hold) and waiting (in hold) ──
        List<IndexedEntry> eligible = new ArrayList<>();
        for (int i = 0; i < queue.size(); i++) {
            QueueEntry e = queue.get(i);
            if (e.waitTimeAt(currentSecond) >= holdSeconds) {
                eligible.add(new IndexedEntry(i, e));
            }
        }

        if (eligible.size() < 2) return matches;

        // ── Score all eligible pairs that pass tolerance gates ─────────────
        List<ScoredPair> scoredPairs = new ArrayList<>();

        for (int i = 0; i < eligible.size(); i++) {
            IndexedEntry ea = eligible.get(i);
            QueueEntry a = ea.entry;
            int waitA = a.waitTimeAt(currentSecond);

            for (int j = i + 1; j < eligible.size(); j++) {
                IndexedEntry eb = eligible.get(j);
                QueueEntry b = eb.entry;
                int waitB = b.waitTimeAt(currentSecond);

                int skillDiff = Math.abs(
                        a.getQueueRequest().getSkillRating() - b.getQueueRequest().getSkillRating());
                int latency = estimateLatency(a, b);

                // ── Tolerance gates: use tighter algorithm-specific windows ──
                // sqrt(weight) amplifies small weight differences at the low end.
                // weight=0.10 → effective=0.316, weight=0.15 → effective=0.387
                // weight=0.25 → effective=0.500, weight=0.40 → effective=0.632
                int maxSkillDiff = Math.min(
                        swtTolerance(weights.weightSkill(), waitA, MAX_SKILL_RANGE),
                        swtTolerance(weights.weightSkill(), waitB, MAX_SKILL_RANGE));
                int maxLatency = Math.min(
                        swtTolerance(weights.weightLatency(), waitA, MAX_LATENCY_RANGE),
                        swtTolerance(weights.weightLatency(), waitB, MAX_LATENCY_RANGE));

                if (skillDiff > maxSkillDiff || latency > maxLatency) {
                    continue;   // pair fails gate — skip
                }

                // Normalize against full fixed ranges
                double skillNorm = skillDiff / (double) MAX_SKILL_RANGE;
                double latNorm = latency / (double) MAX_LATENCY_RANGE;

                // Wait urgency: prefer pairing long-waiters to clear backlog.
                // Normalized so 60 s each → 1.0.
                double waitUrgency = (waitA + waitB) / 120.0;

                double score = weights.weightSkill() * skillNorm
                        + weights.weightLatency() * latNorm
                        - weights.weightWaitTime() * waitUrgency;

                scoredPairs.add(new ScoredPair(ea.queueIndex, eb.queueIndex, score));
            }
        }

        // ── Greedy global assignment — best score first ───────────────────
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

    // ── Algorithm-specific tolerance ────────────────────────────────────

    /**
     * Expanding tolerance with amplified weight sensitivity.
     * <p>
     * Uses {@code sqrt(weight)} instead of linear weight so that even small
     * weight differences (e.g. 10 % vs 15 %) produce noticeably different
     * gate widths. Uses a faster decay rate ({@link #SWT_DECAY_RATE}) so
     * that gates open quickly and players aren't stuck waiting.
     * <pre>
     *   effectiveWeight = sqrt(weight)
     *   initial  = maxRange × (1 − effectiveWeight)
     *   extra    = maxRange × effectiveWeight × (1 − e^(−waitSec / SWT_DECAY_RATE))
     *   total    = initial + extra
     * </pre>
     * <p>
     * Example gate widths at waitSec=0 (as fraction of maxRange):
     * <ul>
     *   <li>weight 10 % → effective 31.6 % → initial 68.4 %</li>
     *   <li>weight 15 % → effective 38.7 % → initial 61.3 %</li>
     *   <li>weight 25 % → effective 50.0 % → initial 50.0 %</li>
     *   <li>weight 40 % → effective 63.2 % → initial 36.8 %</li>
     * </ul>
     */
    private static int swtTolerance(double weight, int waitSec, double maxRange) {
        double ew = Math.sqrt(weight);
        double initial = maxRange * (1.0 - ew);
        double extra = maxRange * ew * (1.0 - Math.exp(-waitSec / SWT_DECAY_RATE));
        return (int) (initial + extra);
    }

    // ── Inner records ────────────────────────────────────────────────────

    private record IndexedEntry(int queueIndex, QueueEntry entry) {
    }

    private record ScoredPair(int i, int j, double score) {
    }
}

