package com.master.matchmaking.persistence;


import com.master.matchmaking.model.entity.GameModeEntity;
import com.master.matchmaking.model.entity.QueueRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

import java.util.List;
import java.util.UUID;

@Repository
public interface QueueRequestRepository extends JpaRepository<QueueRequestEntity, UUID>
{
   List<QueueRequestEntity> findByGameModeAndDayOfTheRequest(GameModeEntity gameMode, LocalDate day );

   long countByGameModeAndDayOfTheRequest( GameModeEntity gameMode, LocalDate day );

   void deleteByGameModeAndDayOfTheRequest( GameModeEntity gameMode, LocalDate day );
}

