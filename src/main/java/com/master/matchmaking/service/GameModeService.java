package com.master.matchmaking.service;


import com.master.matchmaking.model.entity.GameModeEntity;
import com.master.matchmaking.model.enums.GameModeType;
import com.master.matchmaking.persistence.GameModeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GameModeService
{
   private final GameModeRepository repository;

   public GameModeService( GameModeRepository repository )
   {
      this.repository = repository;
   }

   public List<GameModeEntity> findAll()
   {
      return repository.findAll();
   }

   public GameModeEntity findByType( GameModeType type )
   {
      return repository.findByType( type )
            .orElseThrow( () -> new IllegalArgumentException( "Game mode not found: " + type ) );
   }
}

