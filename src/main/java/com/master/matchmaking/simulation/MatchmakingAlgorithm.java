package com.master.matchmaking.simulation;


import com.master.matchmaking.model.simulation.AlgorithmWeights;
import com.master.matchmaking.model.simulation.MatchResult;
import com.master.matchmaking.model.simulation.QueueEntry;

import java.util.List;

/**
 * Interface for all matchmaking algorithms.
 * <p>
 * Each algorithm has a unique search strategy but shares three common concepts:
 * <ol>
 *   <li><b>Expanding tolerance windows</b> – for skill and latency, the maximum
 *       acceptable difference starts tight (controlled by the weight) and widens
 *       asymptotically as the player waits longer.</li>
 *   <li><b>Candidate scoring</b> – among eligible candidates (those within tolerance)
 *       a weighted composite score ranks pairs.</li>
 *   <li><b>Shared match construction</b> – all algorithms produce identical
 *       {@link MatchResult} records.</li>
 * </ol>
 * <p>
 * Tolerance formula for a metric:
 * <pre>
 *   tolerance(weight, waitSec) = maxRange × (1 − weight)
 *                               + maxRange × weight × (1 − e^(−waitSec / DECAY_RATE))
 * </pre>
 * A high weight starts with a tight window and expands slowly; a low weight starts
 * loose and is almost fully expanded from the start.
 */
public interface MatchmakingAlgorithm
{
   // ── normalization ranges ──────────────────────────────────────────────

   /** Full skill-rating range [0..1000]. */
   int MAX_SKILL_RANGE = 1000;

   /** Maximum latency we model (ms). Beyond this is "unplayable". */
   int MAX_LATENCY_RANGE = 300;

   /**
    * Decay rate for the asymptotic expansion (seconds).
    * After ~60 s the window has expanded ~63% of the remaining gap;
    * after ~180 s it's at ~95%.
    */
   double DECAY_RATE = 60.0;

   // ── abstract methods ──────────────────────────────────────────────────

   /** @return human-readable name for reports */
   String name();

   /** @return the weights this algorithm was configured with */
   AlgorithmWeights weights();

   /**
    * Attempt to create matches from the current queue.
    *
    * @param queue         mutable list of waiting players (algorithm removes matched pairs)
    * @param currentSecond current second-of-day in the simulation [0..86399]
    * @return list of matches created in this tick
    */
   List<MatchResult> findMatches(List<QueueEntry> queue, int currentSecond );

   // ── shared tolerance helpers ──────────────────────────────────────────

   /**
    * Expanding skill-difference tolerance for a player who has waited {@code waitSec}.
    * <p>
    * High {@code weightSkill} → starts tight, expands slowly.<br>
    * Low  {@code weightSkill} → starts loose, already near max.
    *
    * @return maximum acceptable absolute skill difference
    */
   default int skillTolerance( int waitSec )
   {
      double w = weights().weightSkill();
      return (int) expandingTolerance( w, waitSec, MAX_SKILL_RANGE );
   }

   /**
    * Expanding latency tolerance for a player who has waited {@code waitSec}.
    *
    * @return maximum acceptable estimated latency (ms)
    */
   default int latencyTolerance( int waitSec )
   {
      double w = weights().weightLatency();
      return (int) expandingTolerance( w, waitSec, MAX_LATENCY_RANGE );
   }

   /**
    * Generic asymptotic tolerance.
    * <pre>
    *   initial = maxRange × (1 − weight)          // loose if weight is low
    *   extra   = maxRange × weight × (1 − e^(−waitSec / DECAY_RATE))
    *   total   = initial + extra                   // approaches maxRange over time
    * </pre>
    */
   private static double expandingTolerance( double weight, int waitSec, double maxRange )
   {
      double initial = maxRange * ( 1.0 - weight );
      double extra = maxRange * weight * ( 1.0 - Math.exp( -waitSec / DECAY_RATE ) );
      return initial + extra;
   }

   // ── shared utilities ──────────────────────────────────────────────────

   /**
    * Compute cross-region latency penalty (ms).
    */
   default int crossRegionPenalty( String regionA, String regionB )
   {
      if ( regionA.equals( regionB ) ) return 0;

      String continentA = regionA.substring( 0, regionA.indexOf( '-' ) );
      String continentB = regionB.substring( 0, regionB.indexOf( '-' ) );

      if ( continentA.equals( continentB ) )
      {
         return switch ( continentA )
         {
            case "EU"   -> 5;
            case "NA"   -> 8;
            case "ASIA" -> 10;
            default     -> 10;
         };
      }

      String pair = continentA.compareTo( continentB ) < 0
            ? continentA + "|" + continentB
            : continentB + "|" + continentA;

      return switch ( pair )
      {
         case "EU|NA"   -> 25;
         case "ASIA|EU" -> 35;
         case "ASIA|NA" -> 35;
         default        -> 35;
      };
   }

   /**
    * Estimated match latency = average of both base latencies + cross-region penalty.
    */
   default int estimateLatency( QueueEntry a, QueueEntry b )
   {
      int avg = ( a.getQueueRequest().getLatencyMs() + b.getQueueRequest().getLatencyMs() ) / 2;
      return avg + crossRegionPenalty( a.getQueueRequest().getRegion(), b.getQueueRequest().getRegion() );
   }

   /**
    * Weighted composite score for a candidate pair.  Lower = better.
    * <p>
    * Used by algorithms that need to pick the "best" pair among several eligible
    * candidates (e.g. StabilityAware). Components are normalized to [0..1].
    */
   default double compositeCost( QueueEntry a, QueueEntry b, int currentSecond )
   {
      AlgorithmWeights w = weights();

      double skillNorm = Math.min(
            Math.abs( a.getQueueRequest().getSkillRating() - b.getQueueRequest().getSkillRating() ) / (double) MAX_SKILL_RANGE, 1.0 );

      double latencyNorm = Math.min( estimateLatency( a, b ) / (double) MAX_LATENCY_RANGE, 1.0 );

      double avgWait = ( a.waitTimeAt( currentSecond ) + b.waitTimeAt( currentSecond ) ) / 2.0;
      double waitPenalty = 1.0 - Math.min( avgWait / 300.0, 1.0 );

      return w.weightSkill() * skillNorm
            + w.weightLatency() * latencyNorm
            + w.weightWaitTime() * waitPenalty;
   }

   /**
    * Build a MatchResult from two queue entries.
    */
   default MatchResult buildMatch( QueueEntry a, QueueEntry b, int currentSecond )
   {
      return new MatchResult(
            a.getQueueRequest(),
            b.getQueueRequest(),
            currentSecond / 60,
            a.waitTimeAt( currentSecond ),
            b.waitTimeAt( currentSecond ),
            Math.abs( a.getQueueRequest().getSkillRating() - b.getQueueRequest().getSkillRating() ),
            estimateLatency( a, b )
      );
   }
}

