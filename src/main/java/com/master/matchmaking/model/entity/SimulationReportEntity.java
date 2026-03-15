package com.master.matchmaking.model.entity;


import com.master.matchmaking.model.embeddable.LatencyStatistics;
import com.master.matchmaking.model.embeddable.SkillStatistics;
import com.master.matchmaking.model.embeddable.WaitTimeStatistics;
import com.master.matchmaking.model.enums.AlgorithmType;
import com.master.matchmaking.model.enums.GameModeType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Stores the result of a single simulation run.
 * <p>
 * Each report captures a snapshot of the algorithm configuration, the game mode
 * it was run for, and all computed statistics. The game mode type and algorithm
 * name are stored as denormalized strings so that reports remain readable even
 * if the referenced configuration is later changed or deleted.
 * <p>
 * Statistics are embedded directly via {@code @Embedded} — no separate tables.
 */

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table( name = "simulation_reports" )
public class SimulationReportEntity
{
   @Id
   @GeneratedValue( strategy = GenerationType.UUID )
   private UUID id;

   @Enumerated( EnumType.STRING )
   @Column( nullable = false )
   private GameModeType gameModeType;

   @Enumerated( EnumType.STRING )
   @Column( nullable = false )
   private AlgorithmType matchmakingAlgorithmName;

   @Column( nullable = false )
   private LocalDate matchesDate;

   @Column( nullable = false )
   private int skillWeight;

   @Column( nullable = false )
   private int waitTimeWeight;

   @Column( nullable = false )
   private int latencyWeight;

   @Column( nullable = false )
   private int totalQueueRequests;

   @Column( nullable = false )
   private int totalMatches;

   @Column( nullable = false )
   private String abandonedMatchesRate;

   @Column( nullable = false )
   private double averageMatchQuality;

   @Embedded
   private WaitTimeStatistics waitTimeStatistics;

   @Embedded
   private SkillStatistics skillStatistics;

   @Embedded
   private LatencyStatistics latencyStatistics;
}

