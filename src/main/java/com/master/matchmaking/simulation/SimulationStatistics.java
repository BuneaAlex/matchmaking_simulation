package com.master.matchmaking.simulation;

import com.master.matchmaking.model.entity.QueueRequestEntity;
import com.master.matchmaking.model.simulation.AlgorithmWeights;
import com.master.matchmaking.model.simulation.MatchResult;

import java.util.*;

/**
 * Computes and holds aggregate statistics for one algorithm run.
 */
public class SimulationStatistics
{
   private final List<MatchResult> matches;
   private final List<QueueRequestEntity> abandonedRequests;
   private final int totalPlayers;

   public SimulationStatistics(List<MatchResult> matches, List<QueueRequestEntity> abandonedRequests, int totalPlayers )
   {
      this.matches = matches;
      this.abandonedRequests = abandonedRequests;
      this.totalPlayers = totalPlayers;
   }

   // ── summary metrics ───────────────────────────────────────────────────

   public int totalMatches()
   {
      return matches.size();
   }


   public int totalAbandoned()
   {
      return abandonedRequests.size();
   }

   public double abandonRate()
   {
      return totalPlayers == 0 ? 0 : ( abandonedRequests.size() / (double) totalPlayers ) * 100.0;
   }

   public double averageWaitTime()
   {
      return matches.stream()
            .mapToDouble( m -> ( m.waitTimeSecondsP1() + m.waitTimeSecondsP2() ) / 2.0 )
            .summaryStatistics().getAverage();
   }

   public double averageSkillDiff()
   {
      return matches.stream()
            .mapToInt( MatchResult::skillDifference )
            .summaryStatistics().getAverage();
   }

   public double averageLatency()
   {
      return matches.stream()
            .mapToInt( MatchResult::estimatedLatencyMs )
            .summaryStatistics().getAverage();
   }

   public double skillQualityStats()
   {
      return matches.stream()
            .mapToInt( MatchResult::skillQuality )
            .summaryStatistics().getAverage();
   }

   public double latencyQualityStats()
   {
      return matches.stream()
            .mapToInt( MatchResult::latencyQuality )
            .summaryStatistics().getAverage();
   }

   public double waitTimeQualityStats()
   {
      return matches.stream()
            .mapToInt( MatchResult::waitTimeQuality )
            .summaryStatistics().getAverage();
   }

   public double matchQualityStats()
   {
      return matches.stream()
            .mapToInt( MatchResult::matchQuality )
            .summaryStatistics().getAverage();
   }



   /**
    * Wait-time distribution: number of matches in each time bucket.
    */
   public Map<String, Integer> waitTimeDistribution()
   {
      Map<String, Integer> buckets = new LinkedHashMap<>();
      buckets.put( "0-5 s", 0 );
      buckets.put( "6-30 s", 0 );
      buckets.put( "31-59 s", 0 );
      buckets.put( ">60 s", 0 );

      for ( MatchResult m : matches )
      {
         double avg = ( m.waitTimeSecondsP1() + m.waitTimeSecondsP2() ) / 2.0;
         if ( avg <= 5 ) buckets.merge( "0-5 s", 1, Integer::sum );
         else if ( avg <= 30 ) buckets.merge( "6-30 s", 1, Integer::sum );
         else if ( avg <= 59 ) buckets.merge( "31-59 s", 1, Integer::sum );
         else buckets.merge( ">60 s", 1, Integer::sum );
      }
      return buckets;
   }

   /**
    * Skill-difference distribution: number of matches in each skill-diff bucket.
    */
   public Map<String, Integer> skillDiffDistribution()
   {
      Map<String, Integer> buckets = new LinkedHashMap<>();
      buckets.put( "0-25", 0 );
      buckets.put( "26-75", 0 );
      buckets.put( "76-150", 0 );
      buckets.put( "151-250", 0 );
      buckets.put( ">250", 0 );

      for ( MatchResult m : matches )
      {
         int diff = m.skillDifference();
         if ( diff <= 25 ) buckets.merge( "0-25", 1, Integer::sum );
         else if ( diff <= 75 ) buckets.merge( "26-75", 1, Integer::sum );
         else if ( diff <= 150 ) buckets.merge( "76-150", 1, Integer::sum );
         else if ( diff <= 250 ) buckets.merge( "151-250", 1, Integer::sum );
         else buckets.merge( ">250", 1, Integer::sum );
      }
      return buckets;
   }

   /**
    * Latency distribution: number of matches in each latency bucket.
    */
   public Map<String, Integer> latencyDistribution()
   {
      Map<String, Integer> buckets = new LinkedHashMap<>();
      buckets.put( "10-30 ms", 0 );
      buckets.put( "31-60 ms", 0 );
      buckets.put( "61-100 ms", 0 );
      buckets.put( "101-180 ms", 0 );
      buckets.put( ">180 ms", 0 );

      for ( MatchResult m : matches )
      {
         int lat = m.estimatedLatencyMs();
         if ( lat <= 30 ) buckets.merge( "10-30 ms", 1, Integer::sum );
         else if ( lat <= 60 ) buckets.merge( "31-60 ms", 1, Integer::sum );
         else if ( lat <= 100 ) buckets.merge( "61-100 ms", 1, Integer::sum );
         else if ( lat <= 180 ) buckets.merge( "101-180 ms", 1, Integer::sum );
         else buckets.merge( ">180 ms", 1, Integer::sum );
      }
      return buckets;
   }
}

