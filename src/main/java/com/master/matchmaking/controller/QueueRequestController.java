package com.master.matchmaking.controller;

import com.master.matchmaking.model.entity.GameModeEntity;
import com.master.matchmaking.model.entity.QueueRequestEntity;
import com.master.matchmaking.model.enums.GameModeType;
import com.master.matchmaking.service.GameModeService;
import com.master.matchmaking.service.QueueRequestService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping( "/api/queue-requests" )
@CrossOrigin(origins = "http://localhost:3000")
public class QueueRequestController
{
   private final QueueRequestService queueRequestService;
   private final GameModeService gameModeService;

   public QueueRequestController( QueueRequestService queueRequestService,
                                   GameModeService gameModeService )
   {
      this.queueRequestService = queueRequestService;
      this.gameModeService = gameModeService;
   }

   /**
    * GET /api/queue-requests?gameMode=RANKED&date=2026-03-07
    * Returns the queue requests for a given game mode and date.
    */
   @GetMapping
   public List<QueueRequestEntity> getByGameModeAndDay(
         @RequestParam GameModeType gameMode,
         @RequestParam @DateTimeFormat( iso = DateTimeFormat.ISO.DATE ) LocalDate date)
   {
      GameModeEntity mode = gameModeService.findByType( gameMode );
      return queueRequestService.findByGameModeAndDay( mode, date);
   }

   /**
    * POST /api/queue-requests/generate?gameMode=RANKED&count=40000&date=2026-03-07
    * Generates and persists queue requests for the specified game mode and date.
    */
   @PostMapping( "/generate" )
   public Map<String, Object> generate(
         @RequestParam GameModeType gameMode,
         @RequestParam int count,
         @RequestParam @DateTimeFormat( iso = DateTimeFormat.ISO.DATE ) LocalDate date)
   {
      GameModeEntity mode = gameModeService.findByType( gameMode );

      long existing = queueRequestService.countByGameModeAndDay( mode, date);
      if ( existing > 0 )
      {
         Map<String, Object> result = new LinkedHashMap<>();
         result.put( "status", "ALREADY_EXISTS" );
         result.put( "gameMode", gameMode.name() );
         result.put( "day", date.toString() );
         result.put( "existingCount", existing );
         result.put( "message", "Queue requests already exist for this game mode and day. "
               + "Delete them first if you want to regenerate." );
         return result;
      }

      queueRequestService.generateAndSave( mode, count, date);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put( "status", "CREATED" );
      result.put( "gameMode", gameMode.name() );
      result.put( "day", date.toString() );
      result.put( "count", count );
      return result;
   }

   @DeleteMapping
   public void delete(@RequestParam GameModeType gameMode,
                      @RequestParam @DateTimeFormat( iso = DateTimeFormat.ISO.DATE ) LocalDate date)
   {
      GameModeEntity mode = gameModeService.findByType( gameMode );
      queueRequestService.deleteByGameModeAndDay(mode,date);
   }
}

