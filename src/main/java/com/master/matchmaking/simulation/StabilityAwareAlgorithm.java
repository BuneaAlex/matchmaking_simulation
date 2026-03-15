package com.master.matchmaking.simulation;

import com.master.matchmaking.model.simulation.AlgorithmWeights;
import com.master.matchmaking.model.simulation.MatchResult;
import com.master.matchmaking.model.simulation.QueueEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Stability-Aware Matchmaking.
 * <p>
 * <b>Strategy:</b> designed for games with a <em>lower active player count</em>.
 * The algorithm deliberately waits until the queue has at least
 * {@code minQueueSize} players (or someone has exceeded
 * {@code forceMatchAfterSeconds}) before it attempts to match. It then picks
 * the globally best pair from the entire pool using the shared composite cost
 * function, with both skill and latency tolerance windows enforced.
 * <p>
 * This "pool-and-pick" strategy sacrifices match speed for overall match
 * quality — by waiting for more candidates, it has a larger pool to find
 * better combinations. The weights control:
 * <ul>
 *   <li>Skill weight → how tightly skill must match (tolerance window)</li>
 *   <li>Latency weight → how tightly latency must match</li>
 *   <li>Wait-time weight → how quickly the algorithm falls back to forced matching</li>
 * </ul>
 */
public class StabilityAwareAlgorithm implements MatchmakingAlgorithm
{
   private final AlgorithmWeights weights;
   private final int minQueueSize;
   private final int forceMatchAfterSeconds;

   public StabilityAwareAlgorithm( AlgorithmWeights weights, int minQueueSize, int forceMatchAfterSeconds )
   {
      this.weights = weights;
      this.minQueueSize = minQueueSize;
      this.forceMatchAfterSeconds = forceMatchAfterSeconds;
   }

   public StabilityAwareAlgorithm( AlgorithmWeights weights )
   {
      // forceMatchAfterSeconds scales inversely with wait-time weight:
      // high wait weight (0.5) → force at 40s; low wait weight (0.2) → force at 100s
      this( weights, 6, (int) ( 20 / weights.weightWaitTime() ) );
   }

   @Override
   public String name()
   {
      return "Stability-Aware";
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

      while ( queue.size() >= 2 )
      {
         // Should we attempt matching this tick?
         boolean shouldMatch = queue.size() >= minQueueSize;
         if ( !shouldMatch )
         {
            for ( QueueEntry e : queue )
            {
               if ( e.waitTimeAt( currentSecond ) >= forceMatchAfterSeconds )
               {
                  shouldMatch = true;
                  break;
               }
            }
         }
         if ( !shouldMatch ) break;

         // Find the globally best pair using composite cost, gated by tolerances
         int bestI = -1, bestJ = -1;
         double bestCost = Double.MAX_VALUE;

         for ( int i = 0; i < queue.size(); i++ )
         {
            QueueEntry a = queue.get( i );
            int waitA = a.waitTimeAt( currentSecond );
            int skillTolA = skillTolerance( waitA );
            int latTolA = latencyTolerance( waitA );

            for ( int j = i + 1; j < queue.size(); j++ )
            {
               QueueEntry b = queue.get( j );
               int waitB = b.waitTimeAt( currentSecond );

               int skillDiff = Math.abs( a.getQueueRequest().getSkillRating() - b.getQueueRequest().getSkillRating() );
               int effectiveSkillTol = Math.max( skillTolA, skillTolerance( waitB ) );
               if ( skillDiff > effectiveSkillTol ) continue;

               int latency = estimateLatency( a, b );
               int effectiveLatTol = Math.max( latTolA, latencyTolerance( waitB ) );
               if ( latency > effectiveLatTol ) continue;

               double cost = compositeCost( a, b, currentSecond );
               if ( cost < bestCost )
               {
                  bestCost = cost;
                  bestI = i;
                  bestJ = j;
               }
            }
         }

         // If no pair passed tolerance gates, try again without gates (forced mode)
         if ( bestI == -1 )
         {
            boolean anyForced = false;
            for ( QueueEntry e : queue )
            {
               if ( e.waitTimeAt( currentSecond ) >= forceMatchAfterSeconds )
               {
                  anyForced = true;
                  break;
               }
            }
            if ( !anyForced ) break;

            // Forced: pick the best pair ignoring tolerances
            for ( int i = 0; i < queue.size(); i++ )
            {
               for ( int j = i + 1; j < queue.size(); j++ )
               {
                  double cost = compositeCost( queue.get( i ), queue.get( j ), currentSecond );
                  if ( cost < bestCost )
                  {
                     bestCost = cost;
                     bestI = i;
                     bestJ = j;
                  }
               }
            }
         }

         if ( bestI == -1 ) break;

         QueueEntry a = queue.get( bestI );
         QueueEntry b = queue.get( bestJ );
         queue.remove( bestJ );
         queue.remove( bestI );

         matches.add( buildMatch( a, b, currentSecond ) );
      }

      return matches;
   }
}

