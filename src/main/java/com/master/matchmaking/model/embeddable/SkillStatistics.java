package com.master.matchmaking.model.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill-difference statistics for a simulation report.
 * Embedded directly into the {@code simulation_reports} table.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class SkillStatistics
{
   @Column( name = "avg_skill_quality" )
   private double averageSkillQuality;

   @Column( name = "avg_skill_diff" )
   private double averageSkillDiff;

   @Column( name = "skill_0_25_pct" )
   private double skillDiffBetween0And25Percentage;

   @Column( name = "skill_26_75_pct" )
   private double skillDiffBetween26And75Percentage;

   @Column( name = "skill_76_150_pct" )
   private double skillDiffBetween76And150Percentage;

   @Column( name = "skill_151_250_pct" )
   private double skillDiffBetween151And250Percentage;

   @Column( name = "skill_gt250_pct" )
   private double skillDiffMoreThan250Percentage;
}

