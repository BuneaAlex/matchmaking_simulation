package com.master.matchmaking.service;

import com.master.matchmaking.exceptions.ReportGenerationException;
import com.master.matchmaking.model.embeddable.LatencyStatistics;
import com.master.matchmaking.model.embeddable.SkillStatistics;
import com.master.matchmaking.model.embeddable.WaitTimeStatistics;
import com.master.matchmaking.model.entity.GameModeEntity;
import com.master.matchmaking.model.entity.MatchmakingAlgorithmEntity;
import com.master.matchmaking.model.entity.QueueRequestEntity;
import com.master.matchmaking.model.entity.SimulationReportEntity;
import com.master.matchmaking.model.enums.AlgorithmType;
import com.master.matchmaking.model.enums.GameModeType;
import com.master.matchmaking.model.simulation.AlgorithmWeights;
import com.master.matchmaking.model.simulation.ReportRequestDTO;
import com.master.matchmaking.persistence.GameModeRepository;
import com.master.matchmaking.persistence.QueueRequestRepository;
import com.master.matchmaking.persistence.SimulationReportRepository;
import com.master.matchmaking.simulation.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@AllArgsConstructor
public class SimulationReportService {
    private final SimulationReportRepository repository;

    private final QueueRequestRepository queueRequestRepository;

    private final GameModeRepository gameModeRepository;

    @Transactional
    public SimulationReportEntity generateReport(final ReportRequestDTO reportRequestDTO) throws ReportGenerationException {
        //TODO: use a join?

        final Optional<GameModeEntity> gameModeEntityOptional = gameModeRepository.findByType(reportRequestDTO.gameModeType());

        if (gameModeEntityOptional.isPresent()) {
            GameModeEntity gameModeEntity = gameModeEntityOptional.get();
            final List<QueueRequestEntity> queueRequests = queueRequestRepository.findByGameModeAndDayOfTheRequest(gameModeEntity, reportRequestDTO.date());

            MatchmakingAlgorithmEntity matchmakingAlgorithm = gameModeEntity.getMatchmakingAlgorithm();
            AlgorithmWeights weights = new AlgorithmWeights(reportRequestDTO.skillWeight(), reportRequestDTO.latencyWeight(), reportRequestDTO.waitTimeWeight());
            final SimulationEngine simulationEngine = new SimulationEngine(queueRequests, getAlgorithm(matchmakingAlgorithm.getName(), weights));

            simulationEngine.run();

            SimulationStatistics stats = new SimulationStatistics(
                    simulationEngine.getMatches(),
                    simulationEngine.getAbandonedRequests(),
                    queueRequests.size());

            int total = stats.totalMatches();

            Map<String, Integer> wtDist = stats.waitTimeDistribution();

            final WaitTimeStatistics waitTimeStatistics = WaitTimeStatistics.builder()
                    .averageWaitSeconds(stats.averageWaitTime())
                    .averageWaitTimeQuality(stats.waitTimeQualityStats())
                    .waitBetween0And5sPercentage(pct( wtDist.get( "0-5 s" ), total ) )
                    .waitBetween6And30sPercentage(pct( wtDist.get( "6-30 s" ), total ) )
                    .waitBetween31And59sPercentage(pct( wtDist.get( "31-59 s" ), total ) )
                    .waitMoreThan60sPercentage( pct( wtDist.get( ">60 s" ), total ) )
                    .build();

            Map<String, Integer> ltDist = stats.latencyDistribution();

            final LatencyStatistics latencyStatistics = LatencyStatistics.builder()
                    .averageLatencyMs(stats.averageLatency())
                    .averageLatencyQuality(stats.latencyQualityStats())
                    .latencyBetween10And30msPercentage(pct( ltDist.get( "10-30 ms" ), total ))
                    .latencyBetween31And60msPercentage(pct( ltDist.get( "31-60 ms" ), total ))
                    .latencyBetween61And100msPercentage(pct( ltDist.get( "61-100 ms" ), total ))
                    .latencyBetween101And180msPercentage(pct( ltDist.get( "101-180 ms" ), total ))
                    .latencyMoreThan180msPercentage(pct( ltDist.get( ">180 ms" ), total ))
                    .build();

            Map<String, Integer> sdDist = stats.skillDiffDistribution();

            final SkillStatistics skillStatistics = SkillStatistics.builder()
                    .averageSkillDiff(stats.averageSkillDiff())
                    .averageSkillQuality(stats.skillQualityStats())
                    .skillDiffBetween0And25Percentage(pct( sdDist.get( "0-25" ), total ))
                    .skillDiffBetween26And75Percentage(pct( sdDist.get( "26-75" ), total ))
                    .skillDiffBetween76And150Percentage(pct( sdDist.get( "76-150" ), total ))
                    .skillDiffBetween151And250Percentage(pct( sdDist.get( "151-250" ), total ))
                    .skillDiffMoreThan250Percentage(pct( sdDist.get( ">250" ), total ))
                    .build();


            final SimulationReportEntity simulationReport = SimulationReportEntity.builder() //
                    .gameModeType(gameModeEntity.getType())
                    .matchmakingAlgorithmName(matchmakingAlgorithm.getName())
                    .matchesDate(reportRequestDTO.date())
                    .skillWeight((int) (weights.weightSkill() * 100))
                    .waitTimeWeight((int) (weights.weightWaitTime() * 100))
                    .latencyWeight((int) (weights.weightLatency() * 100))
                    .totalQueueRequests(queueRequests.size())
                    .totalMatches(total)
                    .abandonedMatchesRate(stats.totalAbandoned() + " - " + stats.abandonRate() + "%")
                    .averageMatchQuality(stats.matchQualityStats())
                    .waitTimeStatistics(waitTimeStatistics)
                    .skillStatistics(skillStatistics)
                    .latencyStatistics(latencyStatistics)
                    .build();

            return repository.save(simulationReport);
        }

        throw new ReportGenerationException("Game mode type not found");
    }

    public List<SimulationReportEntity> findAll() {
        return repository.findAll();
    }

    public List<SimulationReportEntity> findByDate(LocalDate date) {
        return repository.findByMatchesDate(date);
    }

    public List<SimulationReportEntity> findByGameModeType(GameModeType gameModeType) {
        return repository.findByGameModeType(gameModeType);
    }

    public List<SimulationReportEntity> findByMultipleFilters(LocalDate date, GameModeType gameModeType) {
        return repository.findByMatchesDateAndGameModeType(date, gameModeType);
    }

    private MatchmakingAlgorithm getAlgorithm(AlgorithmType type, AlgorithmWeights weights) {
        return switch (type) {
            case SKILL_BASED -> new SkillBasedAlgorithm(weights);
            case LATENCY_BASED -> new LatencyBasedAlgorithm(weights);
            case SHORT_WAIT_TIME -> new ShortWaitTimeAlgorithm(weights);
            case STABILITY_AWARE -> new StabilityAwareAlgorithm(weights);
        };
    }

    private double pct( int count, int total )
    {
        return total == 0 ? 0 : ( count / (double) total ) * 100.0;
    }
}

