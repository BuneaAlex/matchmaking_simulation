package com.master.matchmaking.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a single queue request — a player entering the matchmaking queue
 * for a specific game mode on a specific day.
 * <p>
 * This is the raw input data for the simulation. Each record contains the
 * player's skill rating, network characteristics and queue behaviour parameters.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table( name = "queue_requests" )
public class QueueRequestEntity
{
   @Id
   @GeneratedValue( strategy = GenerationType.UUID )
   private UUID id;

   @ManyToOne( fetch = FetchType.LAZY )
   @JoinColumn( name = "game_mode_id", nullable = false )
   private GameModeEntity gameMode;

   @Column( nullable = false )
   private int skillRating;

   @Column( nullable = false )
   private String region;

   @Column( nullable = false )
   private int latencyMs;

   @Column( nullable = false )
   private int joinTimeSeconds;

   @Column( nullable = false )
   private int patienceSeconds;

   @Column( nullable = false )
   private LocalDate dayOfTheRequest;
}

