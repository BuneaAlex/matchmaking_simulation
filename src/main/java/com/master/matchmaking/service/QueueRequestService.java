package com.master.matchmaking.service;

import com.master.matchmaking.model.entity.GameModeEntity;
import com.master.matchmaking.model.entity.QueueRequestEntity;
import com.master.matchmaking.persistence.QueueRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates and persists queue requests for a given game mode and day.
 * Uses the same realistic distribution logic as the console module's PlayerGenerator.
 */
@Service
public class QueueRequestService
{
   private static final String[] REGIONS = {
         "EU-NORTH", "EU-SOUTH", "EU-EAST", "EU-WEST",
         "NA-NORTH", "NA-SOUTH", "NA-EAST", "NA-WEST",
         "ASIA-EAST", "ASIA-WEST"
   };

   private final QueueRequestRepository repository;

   public QueueRequestService( QueueRequestRepository repository )
   {
      this.repository = repository;
   }

   public List<QueueRequestEntity> findByGameModeAndDay(GameModeEntity gameMode, LocalDate day )
   {
      return repository.findByGameModeAndDayOfTheRequest( gameMode, day );
   }

   public long countByGameModeAndDay( GameModeEntity gameMode, LocalDate day )
   {
      return repository.countByGameModeAndDayOfTheRequest( gameMode, day );
   }

   /**
    * Generates and saves {@code count} queue requests for the given game mode and day.
    * The seed is derived from the date so the same date always produces the same pool.
    */
   @Transactional
   public void generateAndSave(GameModeEntity gameMode, int count, LocalDate day )
   {
      long seed = seedForDate( day, gameMode.getType().name() );
      Random random = new Random( seed );

      List<QueueRequestEntity> requests = new ArrayList<>( count );
      for ( int i = 0; i < count; i++ )
      {
         int skill = clamp( (int) ( random.nextGaussian() * 150 + 500 ), 0, 1000 );
         String region = REGIONS[weightedRegion( random )];
         int latency = generateLatency( random );
         int joinTimeSeconds = generateJoinTimeSeconds( random );
         int patience = clamp( (int) ( random.nextGaussian() * 60 + 120 ), 30, 300 );

         requests.add(
                 QueueRequestEntity.builder()
                         .gameMode(gameMode)
                         .skillRating(skill)
                         .region(region)
                         .latencyMs(latency)
                         .joinTimeSeconds(joinTimeSeconds)
                         .patienceSeconds(patience)
                         .dayOfTheRequest(day)
                         .build());
      }

      repository.saveAll(requests);
   }

   @Transactional
   public void deleteByGameModeAndDay( GameModeEntity gameMode, LocalDate day )
   {
      repository.deleteByGameModeAndDayOfTheRequest( gameMode, day );
   }

   private static long seedForDate( LocalDate date, String modeName )
   {
      return date.toEpochDay() * 31L + modeName.hashCode();
   }

   private static int weightedRegion( Random random )
   {
      double r = random.nextDouble();
      if ( r < 0.10 ) return 0;
      if ( r < 0.20 ) return 1;
      if ( r < 0.30 ) return 2;
      if ( r < 0.40 ) return 3;
      if ( r < 0.50 ) return 4;
      if ( r < 0.60 ) return 5;
      if ( r < 0.70 ) return 6;
      if ( r < 0.80 ) return 7;
      if ( r < 0.90 ) return 8;
      return 9;
   }

   private static int generateLatency( Random random )
   {
      double r = random.nextDouble();
      if ( r < 0.30 ) return 20 + random.nextInt( 11 );
      else if ( r < 0.70 ) return 30 + random.nextInt( 31 );
      else if ( r < 0.95 ) return 60 + random.nextInt( 41 );
      else return 100 + random.nextInt( 81 );
   }

   private static int generateJoinTimeSeconds( Random random )
   {
      double peakSecond = random.nextBoolean() ? 43200.0 : 72000.0;
      double stdDev = 10800.0;
      int second = (int) ( random.nextGaussian() * stdDev + peakSecond );
      second = ( ( second % 86400 ) + 86400 ) % 86400;
      return second;
   }

   private static int clamp( int value, int min, int max )
   {
      return Math.max( min, Math.min( max, value ) );
   }
}

