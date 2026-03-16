package com.master.matchmaking.persistence;


import com.master.matchmaking.model.entity.SimulationReportEntity;
import com.master.matchmaking.model.enums.GameModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SimulationReportRepository extends JpaRepository<SimulationReportEntity, UUID> {
    List<SimulationReportEntity> findByMatchesDate(LocalDate date);

    List<SimulationReportEntity> findByGameModeType(GameModeType gameModeType);

    List<SimulationReportEntity> findByMatchesDateAndGameModeType(LocalDate date, GameModeType gameModeType);

    Optional<SimulationReportEntity> findByGameModeTypeAndMatchesDateAndSkillWeightAndWaitTimeWeightAndLatencyWeight(GameModeType gameModeType, LocalDate date, int skillWeight, int waitTimeWeight, int latencyWeight);
}

