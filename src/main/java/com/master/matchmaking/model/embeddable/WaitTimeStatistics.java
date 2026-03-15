package com.master.matchmaking.model.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wait-time statistics for a simulation report.
 * Embedded directly into the {@code simulation_reports} table.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class WaitTimeStatistics
{
   @Column( name = "avg_wait_time_quality" )
   private double averageWaitTimeQuality;

   @Column( name = "avg_wait_seconds" )
   private double averageWaitSeconds;

   @Column( name = "wait_0_5s_pct" )
   private double waitBetween0And5sPercentage;

   @Column( name = "wait_6_30s_pct" )
   private double waitBetween6And30sPercentage;

   @Column( name = "wait_31_59s_pct" )
   private double waitBetween31And59sPercentage;

   @Column( name = "wait_gt60s_pct" )
   private double waitMoreThan60sPercentage;
}

