package com.master.matchmaking.simulation;

import com.master.matchmaking.model.simulation.AlgorithmWeights;
import com.master.matchmaking.model.simulation.MatchResult;
import com.master.matchmaking.model.simulation.QueueEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Latency-Based Matchmaking.
 * <p>
 * <b>Strategy:</b> sorts players by region then base latency so that
 * same-region / low-latency players are adjacent. For each player the algorithm
 * scans candidates and uses an <em>expanding latency-tolerance window</em>
 * as the primary gate.
 * <p>
 * The latency weight controls how tight the initial window is:
 * <ul>
 *   <li>Latency weight 80% → initial tolerance ~60 ms (tight), slow expansion</li>
 *   <li>Latency weight 50% → initial tolerance ~150 ms (loose), expands fast</li>
 * </ul>
 * Among candidates within the latency window, the one with the smallest skill
 * difference (subject to skill tolerance) is preferred. This creates the tradeoff:
 * higher latency weight → better latency but potentially larger skill gaps and
 * longer waits.
 */
public class LatencyBasedAlgorithm implements MatchmakingAlgorithm
{
   private final AlgorithmWeights weights;

   public LatencyBasedAlgorithm( AlgorithmWeights weights )
   {
      this.weights = weights;
   }

   @Override
   public String name()
   {
      return "Latency-Based";
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

      // Sort by region (primary) then base latency (secondary)
      queue.sort( Comparator.comparing( ( QueueEntry e ) -> e.getQueueRequest().getRegion() )
            .thenComparingInt( e -> e.getQueueRequest().getLatencyMs() ) );

      boolean[] matched = new boolean[queue.size()];

      for ( int i = 0; i < queue.size(); i++ )
      {
         if ( matched[i] ) continue;
         QueueEntry a = queue.get( i );

         int waitA = a.waitTimeAt( currentSecond );
         int latTolA = latencyTolerance( waitA );
         int skillTolA = skillTolerance( waitA );

         int bestIdx = -1;
         int bestScore = Integer.MAX_VALUE;

         for ( int j = i + 1; j < queue.size(); j++ )
         {
            if ( matched[j] ) continue;
            QueueEntry b = queue.get( j );

            int waitB = b.waitTimeAt( currentSecond );
            int effectiveLatTol = Math.max( latTolA, latencyTolerance( waitB ) );

            int latency = estimateLatency( a, b );

            // Primary gate: latency tolerance
            if ( latency > effectiveLatTol ) continue;

            // Skill check (soft gate)
            int skillDiff = Math.abs( a.getQueueRequest().getSkillRating() - b.getQueueRequest().getSkillRating() );
            int effectiveSkillTol = Math.max( skillTolA, skillTolerance( waitB ) );

            // Score: primarily latency, with skill diff as tiebreaker
            int skillPenalty = skillDiff > effectiveSkillTol ? 5000 : skillDiff;
            int score = latency * 10 + skillPenalty;

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

