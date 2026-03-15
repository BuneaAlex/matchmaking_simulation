package com.master.matchmaking.model.entity;


import com.master.matchmaking.model.enums.GameModeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.util.UUID;

/**
 * Represents a game mode within the simulated game.
 * <p>
 * Each game mode has a type (RANKED, CASUAL, TOURNAMENT) and is linked to a
 * specific matchmaking algorithm that governs how queue requests are paired.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table( name = "game_modes" )
public class GameModeEntity
{
   @Id
   @GeneratedValue( strategy = GenerationType.UUID )
   private UUID id;

   @Enumerated( EnumType.STRING )
   @Column( nullable = false, unique = true )
   private GameModeType type;

   @ManyToOne( fetch = FetchType.LAZY )
   @JoinColumn( name = "matchmaking_algorithm_id", nullable = false )
   private MatchmakingAlgorithmEntity matchmakingAlgorithm;
}

