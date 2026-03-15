package com.master.matchmaking.model.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Latency statistics for a simulation report.
 * Embedded directly into the {@code simulation_reports} table.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class LatencyStatistics
{
   @Column( name = "avg_latency_quality" )
   private double averageLatencyQuality;

   @Column( name = "avg_latency_ms" )
   private double averageLatencyMs;

   @Column( name = "latency_10_30ms_pct" )
   private double latencyBetween10And30msPercentage;

   @Column( name = "latency_31_60ms_pct" )
   private double latencyBetween31And60msPercentage;

   @Column( name = "latency_61_100ms_pct" )
   private double latencyBetween61And100msPercentage;

   @Column( name = "latency_101_180ms_pct" )
   private double latencyBetween101And180msPercentage;

   @Column( name = "latency_gt180ms_pct" )
   private double latencyMoreThan180msPercentage;
}

