package com.master.matchmaking.controller;

import com.master.matchmaking.model.entity.MatchmakingAlgorithmEntity;
import com.master.matchmaking.service.MatchmakingAlgorithmService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

@RequestMapping( "/api/algorithms" )
@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class MatchmakingAlgorithmController
{
   private final MatchmakingAlgorithmService service;

   public MatchmakingAlgorithmController( MatchmakingAlgorithmService service )
   {
      this.service = service;
   }

   @GetMapping
   public List<MatchmakingAlgorithmEntity> getAll()
   {
      return service.findAll();
   }

   @GetMapping( "/{id}" )
   public MatchmakingAlgorithmEntity getById( @PathVariable UUID id )
   {
      return service.findById( id );
   }
}








