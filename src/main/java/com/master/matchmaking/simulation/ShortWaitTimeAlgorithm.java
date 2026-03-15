package com.master.matchmaking.simulation;


import com.master.matchmaking.model.simulation.AlgorithmWeights;
import com.master.matchmaking.model.simulation.MatchResult;
import com.master.matchmaking.model.simulation.QueueEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Short Wait-Time Matchmaking.
 * <p>
 * <b>Strategy:</b> always processes the longest-waiting player first (FIFO).
 * For each player, the algorithm greedily picks the best available partner
 * based on a <em>wait-time-weighted score</em> — prioritizing candidates that
 * are also waiting a long time, while using skill and latency tolerances as
 * soft gates.
 * <p>
 * The wait-time weight controls how aggressively the algorithm trades quality
 * for speed:
 * <ul>
 *   <li>Wait weight 80% → very loose skill/latency tolerances from the start,
 *       matches happen fast even if quality is mediocre</li>
 *   <li>Wait weight 50% → moderate tolerances, waits a bit longer for
 *       decent skill/latency matches</li>
 * </ul>
 * The key difference from other algorithms: this one scans for the
 * <em>best available</em> partner (lowest composite score) rather than
 * enforcing hard tolerance gates. The wait-time dominance in the score means
 * long-waiters get matched quickly regardless of other metrics.
 */
public class ShortWaitTimeAlgorithm implements MatchmakingAlgorithm
{
   private final AlgorithmWeights weights;

   public ShortWaitTimeAlgorithm( AlgorithmWeights weights )
   {
      this.weights = weights;
   }

   @Override
   public String name()
   {
      return "Short Wait-Time";
   }

   @Override
   public AlgorithmWeights weights()
   {
      return weights;
   }

   @Override
   public List<MatchResult> findMatches(List<QueueEntry> queue, int currentSecond )
   {
      List<MatchResult> matches = new ArrayList<>();

      // Sort by join time ascending → longest-waiting players first
      queue.sort( Comparator.comparingInt( QueueEntry::getJoinTimeSeconds ) );

      boolean[] matched = new boolean[queue.size()];

      for ( int i = 0; i < queue.size(); i++ )
      {
         if ( matched[i] ) continue;
         QueueEntry a = queue.get( i );

         int waitA = a.waitTimeAt( currentSecond );
         int skillTolA = skillTolerance( waitA );
         int latTolA = latencyTolerance( waitA );

         int bestIdx = -1;
         double bestScore = Double.MAX_VALUE;

         for ( int j = i + 1; j < queue.size(); j++ )
         {
            if ( matched[j] ) continue;
            QueueEntry b = queue.get( j );

            int waitB = b.waitTimeAt( currentSecond );

            // Use skill & latency tolerances as soft scoring modifiers, not hard gates
            int skillDiff = Math.abs( a.getQueueRequest().getSkillRating() - b.getQueueRequest().getSkillRating() );
            int latency = estimateLatency( a, b );

            int effectiveSkillTol = Math.max( skillTolA, skillTolerance( waitB ) );
            int effectiveLatTol = Math.max( latTolA, latencyTolerance( waitB ) );

            // Composite score weighted heavily towards reducing wait time.
            // Skill and latency are normalized penalties; wait-time urgency
            // (how long BOTH players have been waiting) is a reward.
            double skillPenalty = skillDiff > effectiveSkillTol
                  ? 2.0  // out of tolerance: fixed high penalty
                  : skillDiff / (double) MAX_SKILL_RANGE;

            double latPenalty = latency > effectiveLatTol
                  ? 2.0
                  : latency / (double) MAX_LATENCY_RANGE;

            // Wait reward: higher combined wait = lower score (better)
            double waitReward = ( waitA + waitB ) / 600.0; // normalized to ~[0..1]

            double score = weights.weightSkill() * skillPenalty
                  + weights.weightLatency() * latPenalty
                  - weights.weightWaitTime() * waitReward;

            if ( score < bestScore )
            {
               bestScore = score;
               bestIdx = j;
            }
         }

         if ( bestIdx != -1 )
         {
            matched[i] = true;
            matched[bestIdx] = true;
            matches.add( buildMatch( a, queue.get( bestIdx ), currentSecond ) );
         }
      }

      // Remove matched entries backwards
      for ( int i = queue.size() - 1; i >= 0; i-- )
      {
         if ( matched[i] ) queue.remove( i );
      }

      return matches;
   }
}

