package com.master.matchmaking.model.simulation;

import com.master.matchmaking.model.entity.QueueRequestEntity;

/**
 * Represents the result of a single match produced by a matchmaking algorithm.
 *
 * @param queueRequest1      first matched queue request
 * @param queueRequest2      second matched queue request
 * @param matchTimeMinutes   minute-of-day when the match was created
 * @param waitTimeSecondsP1  how long player 1 waited in queue (seconds)
 * @param waitTimeSecondsP2  how long player 2 waited in queue (seconds)
 * @param skillDifference    absolute skill rating difference between the two players
 * @param estimatedLatencyMs estimated match latency (average of both players' latency, plus cross-region penalty)
 */
public record MatchResult(
        QueueRequestEntity queueRequest1,
        QueueRequestEntity queueRequest2,
        int matchTimeMinutes,
        int waitTimeSecondsP1,
        int waitTimeSecondsP2,
        int skillDifference,
        int estimatedLatencyMs
) {
    /**
     * Average wait time of the two players in this match, in seconds.
     */
    public double averageWaitTime() {
        return (waitTimeSecondsP1 + waitTimeSecondsP2) / 2.0;
    }

    /**
     * Skill quality score [0 .. 100]. 100 = perfect skill match, 0 = worst possible.
     * Based on skill difference relative to a worst-case threshold of 250.
     * Any skill difference >= 250 is considered a severe mismatch (score 0).
     */
    public int skillQuality() {
        return (int) ((1.0 - Math.min(skillDifference / 250.0, 1.0)) * 100);
    }

    /**
     * The cross-region latency penalty (ms) added by this match — the "avoidable"
     * part of the estimated latency. This is the difference between the estimated
     * match latency and the average of both players' base latencies.
     * <p>
     * Same-region match → 0 ms, same-continent → 5–10 ms, cross-continent → 25–35 ms.
     */
    public int crossRegionPenaltyMs() {
        int avgBase = (queueRequest1.getLatencyMs() + queueRequest2.getLatencyMs()) / 2;
        return Math.max(estimatedLatencyMs - avgBase, 0);
    }

    /**
     * Latency quality score [0 .. 100]. Measures <b>how well the algorithm matched
     * players by region and latency similarity</b>, not their absolute connection quality.
     * <p>
     * The score is composed of two parts:
     * <ol>
     *   <li><b>Region tier (0 – 70 points)</b> — based on the cross-region penalty:
     *     <ul>
     *       <li>Same region (penalty 0 ms) → <b>70</b></li>
     *       <li>Same continent (penalty 1–10 ms) → <b>50</b></li>
     *       <li>Cross-continent nearby (penalty 11–30 ms, e.g. EU↔NA) → <b>20</b></li>
     *       <li>Cross-continent far (penalty &gt; 30 ms, e.g. EU↔ASIA) → <b>0</b></li>
     *     </ul>
     *   </li>
     *   <li><b>Latency similarity bonus (0 – 30 points)</b> — based on how close
     *       the two players' base latencies are. A difference of 0 ms gives the
     *       full 30 points; a difference ≥ 40 ms gives 0.
     *       This rewards the algorithm for pairing players with similar connection
     *       quality, e.g. matching 30 ms + 35 ms (bonus 26) is better than
     *       10 ms + 50 ms (bonus 0).</li>
     * </ol>
     */
    public int latencyQuality() {
        // Region tier (0–70)
        int penalty = crossRegionPenaltyMs();
        int regionScore;
        if (penalty == 0) regionScore = 70;        // same region
        else if (penalty <= 10) regionScore = 50;   // same continent
        else if (penalty <= 30) regionScore = 20;   // cross-continent nearby
        else regionScore = 0;                          // cross-continent far

        // Latency similarity bonus (0–30): how close are the two players' base latencies?
        // Reference of 40 ms means any diff >= 40 ms scores 0 bonus.
        int latencyDiff = Math.abs(queueRequest1.getLatencyMs() - queueRequest2.getLatencyMs());
        int similarityBonus = (int) ((1.0 - Math.min(latencyDiff / 40.0, 1.0)) * 30);

        return regionScore + similarityBonus;
    }

    /**
     * Wait-time quality score [0 .. 100]. 100 = instant match (0 s), 0 = waited >= 60 s.
     * Based on average wait time relative to a maximum reference of 60 seconds (1v1 game).
     */
    public int waitTimeQuality() {
        return (int) ((1.0 - Math.min(averageWaitTime() / 60.0, 1.0)) * 100);
    }

    /**
     * Overall match quality score [0 .. 100].
     * Equal-weighted composite of skill quality, latency quality and wait-time quality.
     */
    public int matchQuality() {
        return (skillQuality() + latencyQuality() + waitTimeQuality()) / 3;
    }
}

