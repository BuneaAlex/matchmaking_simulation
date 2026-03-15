package com.master.matchmaking.simulation;

import com.master.matchmaking.model.simulation.AlgorithmWeights;
import com.master.matchmaking.model.simulation.MatchResult;
import com.master.matchmaking.model.simulation.QueueEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Skill-Based Matchmaking (SBMM).
 * <p>
 * <b>Strategy:</b> sorts the queue by skill rating so that similar players are
 * adjacent, then scans outward from each player looking for the best partner
 * within an <em>expanding skill-tolerance window</em>.
 * <p>
 * The skill weight controls how tight the initial window is:
 * <ul>
 *   <li>Skill weight 80% → initial tolerance ~600 rating points (tight), slow expansion</li>
 *   <li>Skill weight 50% → initial tolerance ~1500 points (loose), expands fast</li>
 * </ul>
 * Among candidates within the skill window, the one with the best latency (subject
 * to the latency tolerance) is preferred. This creates a clear tradeoff: higher
 * skill weight → better skill matches but potentially worse latency and longer waits.
 */
public class SkillBasedAlgorithm implements MatchmakingAlgorithm
{
   private final AlgorithmWeights weights;

   public SkillBasedAlgorithm( AlgorithmWeights weights )
   {
      this.weights = weights;
   }

   @Override
   public String name()
   {
      return "Skill-Based (SBMM)";
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

      // Sort by skill so similar players are adjacent
      queue.sort( Comparator.comparingInt( e -> e.getQueueRequest().getSkillRating() ) );

      boolean[] matched = new boolean[queue.size()];

      for ( int i = 0; i < queue.size(); i++ )
      {
         if ( matched[i] ) continue;
         QueueEntry a = queue.get( i );

         // Compute expanding tolerance windows based on how long A has waited
         int waitA = a.waitTimeAt( currentSecond );
         int skillTolA = skillTolerance( waitA );
         int latTolA = latencyTolerance( waitA );

         int bestIdx = -1;
         int bestScore = Integer.MAX_VALUE; // lower = better (skill diff + latency penalty)

         for ( int j = i + 1; j < queue.size(); j++ )
         {
            if ( matched[j] ) continue;
            QueueEntry b = queue.get( j );

            int skillDiff = Math.abs( a.getQueueRequest().getSkillRating() - b.getQueueRequest().getSkillRating() );

            // Use the wider tolerance of the two players (the longer-waiting one is more flexible)
            int waitB = b.waitTimeAt( currentSecond );
            int effectiveSkillTol = Math.max( skillTolA, skillTolerance( waitB ) );

            if ( skillDiff > effectiveSkillTol )
            {
               break; // sorted by skill — no closer candidate further out
            }

            // Latency check (soft gate — prefer within tolerance, but still consider)
            int latency = estimateLatency( a, b );
            int effectiveLatTol = Math.max( latTolA, latencyTolerance( waitB ) );

            // Score: primarily skill diff, with latency as tiebreaker
            // If latency exceeds tolerance, add a heavy penalty to prefer in-tolerance matches
            int latPenalty = latency > effectiveLatTol ? 5000 : latency;
            int score = skillDiff * 10 + latPenalty;

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

      // Remove matched entries backwards to preserve indices
      for ( int i = queue.size() - 1; i >= 0; i-- )
      {
         if ( matched[i] ) queue.remove( i );
      }

      return matches;
   }
}

