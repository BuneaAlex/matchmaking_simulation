package com.master.matchmaking.model.entity;

import com.master.matchmaking.model.enums.AlgorithmType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Defines a matchmaking algorithm with its name and weight constraints.
 * <p>
 * Each algorithm has default weights and allowed min/max ranges for skill,
 * latency and wait-time weights. All three weights must sum to 100.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table( name = "matchmaking_algorithms" )
public class MatchmakingAlgorithmEntity
{
   @Id
   @GeneratedValue( strategy = GenerationType.UUID )
   private UUID id;

   @Enumerated( EnumType.STRING )
   @Column( nullable = false, unique = true )
   private AlgorithmType name;

   @Column( nullable = false )
   private int defaultSkillWeight;

   @Column( nullable = false )
   private int defaultLatencyWeight;

   @Column( nullable = false )
   private int defaultWaitTimeWeight;

   @Column( nullable = false )
   private int minSkillWeight;

   @Column( nullable = false )
   private int maxSkillWeight;

   @Column( nullable = false )
   private int minLatencyWeight;

   @Column( nullable = false )
   private int maxLatencyWeight;

   @Column( nullable = false )
   private int minWaitTimeWeight;

   @Column( nullable = false )
   private int maxWaitTimeWeight;

   public void validateWeights( int skillWeight, int latencyWeight, int waitTimeWeight )
   {
      if ( skillWeight + latencyWeight + waitTimeWeight != 100 )
      {
         throw new IllegalArgumentException( "Weights must sum to 100, got " +
               ( skillWeight + latencyWeight + waitTimeWeight ) );
      }
      if ( skillWeight < minSkillWeight || skillWeight > maxSkillWeight )
      {
         throw new IllegalArgumentException(
               String.format( "Skill weight %d is out of range [%d – %d] for %s",
                     skillWeight, minSkillWeight, maxSkillWeight, name ) );
      }
      if ( latencyWeight < minLatencyWeight || latencyWeight > maxLatencyWeight )
      {
         throw new IllegalArgumentException(
               String.format( "Latency weight %d is out of range [%d – %d] for %s",
                     latencyWeight, minLatencyWeight, maxLatencyWeight, name ) );
      }
      if ( waitTimeWeight < minWaitTimeWeight || waitTimeWeight > maxWaitTimeWeight )
      {
         throw new IllegalArgumentException(
               String.format( "Wait-time weight %d is out of range [%d – %d] for %s",
                     waitTimeWeight, minWaitTimeWeight, maxWaitTimeWeight, name ) );
      }
   }
}

