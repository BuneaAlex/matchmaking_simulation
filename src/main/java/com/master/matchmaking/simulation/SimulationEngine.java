package com.master.matchmaking.simulation;

import com.master.matchmaking.exceptions.NoQueueRequestsException;
import com.master.matchmaking.model.simulation.MatchResult;
import com.master.matchmaking.model.simulation.QueueEntry;
import com.master.matchmaking.model.entity.QueueRequestEntity;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Discrete-event simulation of one day (86 400 seconds) of matchmaking.
 * <p>
 * The simulation ticks every {@code tickIntervalSeconds} seconds.
 * At each tick:
 * <ol>
 *   <li>Queue requests whose {@code joinTimeSeconds} falls within this tick enter the queue.</li>
 *   <li>Expired requests (patience exceeded) are removed and counted as abandoned.</li>
 *   <li>The matchmaking algorithm runs and produces matches.</li>
 * </ol>
 */
public class SimulationEngine {
    private static final int SECONDS_PER_DAY = 86_400;

    private final List<QueueRequestEntity> allRequests;
    private final MatchmakingAlgorithm algorithm;
    private final int tickIntervalSeconds;

    // ── results ────────────────────────────────────────────────────────────
    @Getter
    private final List<MatchResult> matches;

    @Getter
    private final List<QueueRequestEntity> abandonedRequests;

    public SimulationEngine(List<QueueRequestEntity> allRequests, MatchmakingAlgorithm algorithm, int tickIntervalSeconds) {
        this.allRequests = new ArrayList<>(allRequests);
        this.allRequests.sort(Comparator.comparingInt(QueueRequestEntity::getJoinTimeSeconds));
        this.algorithm = algorithm;
        this.tickIntervalSeconds = tickIntervalSeconds;
        this.matches = new ArrayList<>();
        this.abandonedRequests = new ArrayList<>();
    }

    public SimulationEngine(List<QueueRequestEntity> allRequests, MatchmakingAlgorithm algorithm) {
        this(allRequests, algorithm, 5); // default: tick every 5 seconds
    }

    /**
     * Run the full day simulation.
     */
    public void run() throws NoQueueRequestsException {
        matches.clear();
        abandonedRequests.clear();

        if (this.allRequests.isEmpty()) {
            throw new NoQueueRequestsException();
        }

        List<QueueEntry> queue = new ArrayList<>();
        int cursor = 0;

        for (int second = 0; second < SECONDS_PER_DAY; second += tickIntervalSeconds) {
            int tickEnd = second + tickIntervalSeconds;

            // 1. Enqueue requests whose joinTimeSeconds falls in [second, tickEnd)
            while (cursor < allRequests.size()
                    && allRequests.get(cursor).getJoinTimeSeconds() < tickEnd) {
                QueueRequestEntity req = allRequests.get(cursor);
                queue.add(new QueueEntry(req, req.getJoinTimeSeconds()));
                cursor++;
            }

            // 2. Remove expired (impatient) requests — evaluated at end of tick
            Iterator<QueueEntry> it = queue.iterator();
            while (it.hasNext()) {
                QueueEntry entry = it.next();
                if (entry.hasExpired(tickEnd)) {
                    abandonedRequests.add(entry.getQueueRequest());
                    it.remove();
                }
            }

            // 3. Run the matchmaking algorithm (at end of tick, so all enqueued players have non-negative wait)
            List<MatchResult> tickMatches = algorithm.findMatches(queue, tickEnd);
            matches.addAll(tickMatches);
        }

        // Any requests still in the queue at end of day count as abandoned
        for (QueueEntry e : queue) {
            abandonedRequests.add(e.getQueueRequest());
        }
    }
}
