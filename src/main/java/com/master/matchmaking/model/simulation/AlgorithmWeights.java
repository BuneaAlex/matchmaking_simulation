package com.master.matchmaking.model.simulation;

/**
 * Holds the three weights used by all matchmaking algorithms.
 * <p>
 * Every algorithm uses the same composite cost formula:
 * <pre>
 *   cost = weightSkill    * normalizedSkillDiff
 *        + weightLatency  * normalizedLatency
 *        + weightWaitTime * normalizedWaitTimePenalty
 * </pre>
 * The weights must sum to 1.0 (100%).
 *
 * @param weightSkill    weight for the skill-difference component [0.0 .. 1.0]
 * @param weightLatency  weight for the latency component [0.0 .. 1.0]
 * @param weightWaitTime weight for the wait-time penalty component [0.0 .. 1.0]
 */
public record AlgorithmWeights(
      double weightSkill,
      double weightLatency,
      double weightWaitTime
)
{
   private static final double EPSILON = 0.001;

   public AlgorithmWeights
   {
      if ( weightSkill < 0 || weightLatency < 0 || weightWaitTime < 0 )
      {
         throw new IllegalArgumentException( "All weights must be >= 0" );
      }
      double sum = weightSkill + weightLatency + weightWaitTime;
      if ( Math.abs( sum - 1.0 ) > EPSILON )
      {
         throw new IllegalArgumentException(
               String.format( "Weights must sum to 1.0, but got %.3f (skill=%.2f, latency=%.2f, waitTime=%.2f)",
                     sum, weightSkill, weightLatency, weightWaitTime ) );
      }
   }

   public int getWeightSkill()
   {
      return (int) (weightSkill * 100);
   }


   public int getWeightWaitTime()
   {
      return (int) (weightWaitTime * 100);
   }


   public int getWeightLatency()
   {
      return (int) (weightLatency * 100);
   }

   @Override
   public String toString()
   {
      return String.format( "Weights[skill=%.0f%%, latency=%.0f%%, waitTime=%.0f%%]",
            weightSkill * 100, weightLatency * 100, weightWaitTime * 100 );
   }
}

