package com.master.matchmaking.persistence;
import java.util.Optional;
import java.util.UUID;

import com.master.matchmaking.model.entity.MatchmakingAlgorithmEntity;
import com.master.matchmaking.model.enums.AlgorithmType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchmakingAlgorithmRepository extends JpaRepository<MatchmakingAlgorithmEntity, UUID>
{
    Optional<MatchmakingAlgorithmEntity> findByName(AlgorithmType name );
}


