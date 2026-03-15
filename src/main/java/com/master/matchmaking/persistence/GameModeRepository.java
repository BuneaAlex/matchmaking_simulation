package com.master.matchmaking.persistence;

import com.master.matchmaking.model.entity.GameModeEntity;
import com.master.matchmaking.model.enums.GameModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GameModeRepository extends JpaRepository<GameModeEntity, UUID>
{
   Optional<GameModeEntity> findByType(GameModeType type );
}

