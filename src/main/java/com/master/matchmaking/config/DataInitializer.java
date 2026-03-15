package com.master.matchmaking.config;

import com.master.matchmaking.model.entity.GameModeEntity;
import com.master.matchmaking.model.entity.MatchmakingAlgorithmEntity;
import com.master.matchmaking.model.enums.AlgorithmType;
import com.master.matchmaking.model.enums.GameModeType;
import com.master.matchmaking.persistence.GameModeRepository;
import com.master.matchmaking.persistence.MatchmakingAlgorithmRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds the database with the four matchmaking algorithms and three game modes
 * on application startup (only if they don't already exist).
 * <p>
 * Default mapping:
 * <ul>
 *   <li>RANKED      → Skill-Based (SBMM)</li>
 *   <li>CASUAL      → Short Wait-Time</li>
 *   <li>TOURNAMENT  → Latency-Based</li>
 * </ul>
 */
@Configuration
public class DataInitializer
{
   private static final Logger log = LoggerFactory.getLogger( DataInitializer.class );

   @Bean
   CommandLineRunner seedData( MatchmakingAlgorithmRepository algorithmRepo,
                               GameModeRepository gameModeRepo )
   {
      return args ->
      {
         // ── Algorithms ───────────────────────────────────────────────
         MatchmakingAlgorithmEntity sbmm = findOrCreate( algorithmRepo,
               AlgorithmType.SKILL_BASED, 70, 15, 15, 50, 80, 10, 30, 10, 30 );

         MatchmakingAlgorithmEntity latency = findOrCreate( algorithmRepo,
               AlgorithmType.LATENCY_BASED, 15, 70, 15, 10, 30, 50, 80, 10, 30 );

         MatchmakingAlgorithmEntity shortWait = findOrCreate( algorithmRepo,
               AlgorithmType.SHORT_WAIT_TIME, 20, 20, 60, 10, 40, 10, 40, 50, 80 );

         MatchmakingAlgorithmEntity stability = findOrCreate( algorithmRepo,
               AlgorithmType.STABILITY_AWARE, 34, 33, 33, 20, 50, 20, 50, 20, 50 );

         // ── Game Modes ───────────────────────────────────────────────
         findOrCreateMode( gameModeRepo, GameModeType.RANKED, sbmm );
         findOrCreateMode( gameModeRepo, GameModeType.CASUAL, shortWait );
         findOrCreateMode( gameModeRepo, GameModeType.TOURNAMENT, latency );

         log.info( "Data initialization complete – {} algorithms, {} game modes",
               algorithmRepo.count(), gameModeRepo.count() );
      };
   }

   private MatchmakingAlgorithmEntity findOrCreate( MatchmakingAlgorithmRepository repo,
                                                     AlgorithmType name,
                                                     int defSkill, int defLatency, int defWait,
                                                     int minSkill, int maxSkill,
                                                     int minLat, int maxLat,
                                                     int minWait, int maxWait )
   {
      return repo.findByName( name ).orElseGet( () ->
      {
         log.info( "Creating algorithm: {}", name );
         return repo.save( MatchmakingAlgorithmEntity.builder()
                         .name(name)
                         .defaultSkillWeight(defSkill)
                         .defaultLatencyWeight(defLatency)
                         .defaultWaitTimeWeight(defWait)
                         .minSkillWeight(minSkill)
                         .maxSkillWeight(maxSkill)
                         .minLatencyWeight(minLat)
                         .maxLatencyWeight(maxLat)
                         .minWaitTimeWeight(minWait)
                         .maxWaitTimeWeight(maxWait)

                 .build());
      } );
   }

   private void findOrCreateMode( GameModeRepository repo,
                                   GameModeType type,
                                   MatchmakingAlgorithmEntity algorithm )
   {
      repo.findByType( type ).orElseGet( () ->
      {
         log.info( "Creating game mode: {} → {}", type, algorithm.getName() );
         return repo.save( GameModeEntity.builder().type(type).matchmakingAlgorithm(algorithm).build());
      } );
   }
}

