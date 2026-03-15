package com.master.matchmaking.model.simulation;

import com.master.matchmaking.model.entity.QueueRequestEntity;

/**
 * Represents the result of a single match produced by a matchmaking algorithm.
 *
 * @param queueRequest1     first matched queue request
 * @param queueRequest2     second matched queue request
 * @param matchTimeMinutes  minute-of-day when the match was created
 * @param waitTimeSecondsP1 how long player 1 waited in queue (seconds)
 * @param waitTimeSecondsP2 how long player 2 waited in queue (seconds)
 * @param skillDifference   absolute skill rating difference between the two players
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
)
{
   /**
    * Average wait time of the two players in this match, in seconds.
    */
   public double averageWaitTime()
   {
      return ( waitTimeSecondsP1 + waitTimeSecondsP2 ) / 2.0;
   }

   /**
    * Skill quality score [0 .. 100]. 100 = perfect skill match, 0 = worst possible.
    * Based on skill difference relative to a worst-case threshold of 250.
    * Any skill difference >= 250 is considered a severe mismatch (score 0).
    */
   public int skillQuality()
   {
      return (int) ( ( 1.0 - Math.min( skillDifference / 250.0, 1.0 ) ) * 100 );
   }

   /**
    * Latency quality score [0 .. 100]. 100 = 0 ms latency, 0 = >= 300 ms.
    */
   public int latencyQuality()
   {
      return (int) ( ( 1.0 - Math.min( estimatedLatencyMs / 300.0, 1.0 ) ) * 100 );
   }

   /**
    * Wait-time quality score [0 .. 100]. 100 = instant match (0 s), 0 = waited >= 60 s.
    * Based on average wait time relative to a maximum reference of 60 seconds (1v1 game).
    */
   public int waitTimeQuality()
   {
      return (int) ( ( 1.0 - Math.min( averageWaitTime() / 60.0, 1.0 ) ) * 100 );
   }

   /**
    * Overall match quality score [0 .. 100].
    * Equal-weighted composite of skill quality, latency quality and wait-time quality.
    */
   public int matchQuality()
   {
      return ( skillQuality() + latencyQuality() + waitTimeQuality() ) / 3;
   }
}

