package com.master.matchmaking.controller;

import com.master.matchmaking.exceptions.ReportGenerationException;
import com.master.matchmaking.model.embeddable.LatencyStatistics;
import com.master.matchmaking.model.embeddable.SkillStatistics;
import com.master.matchmaking.model.embeddable.WaitTimeStatistics;
import com.master.matchmaking.model.entity.SimulationReportEntity;
import com.master.matchmaking.model.enums.GameModeType;
import com.master.matchmaking.model.simulation.ReportRequestDTO;
import com.master.matchmaking.service.SimulationReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "http://localhost:3000")
public class SimulationReportController {
    private final SimulationReportService service;

    public SimulationReportController(SimulationReportService service) {
        this.service = service;
    }

    @GetMapping
    public List<SimulationReportEntity> getAll() {
        return service.findAll();
    }


    @GetMapping("/by-date")
    public List<SimulationReportEntity> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.findByDate(date);
    }

    @GetMapping("/by-game-mode")
    public List<SimulationReportEntity> getByGameModeType(@RequestParam GameModeType gameModeType) {
        return service.findByGameModeType(gameModeType);
    }


    @GetMapping("/advanced-filter")
    public List<SimulationReportEntity> getByMultipleFilters(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @RequestParam GameModeType gameModeType) {
        return service.findByMultipleFilters(date, gameModeType);
    }


    @PostMapping
    public ResponseEntity<?> createSimulationReport(@RequestBody ReportRequestDTO reportRequestDTO) {
        try {
            return ResponseEntity.ok(service.generateReport(reportRequestDTO));
        } catch (ReportGenerationException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    @GetMapping("/export-csv")
    public ResponseEntity<String> exportToCsv() {
        List<SimulationReportEntity> reports = service.findAll();
        String filePath = "reports.csv";

        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath, false))) {
            pw.println("id,gameModeType,algorithmName,matchesDate,skillWeight,waitTimeWeight,latencyWeight,"
                    + "totalQueueRequests,totalMatches,abandonedMatchesRate,averageMatchQuality,"
                    + "avgWaitTimeQuality,avgWaitSeconds,wait0_5sPct,wait6_30sPct,wait31_59sPct,waitGt60sPct,"
                    + "avgSkillQuality,avgSkillDiff,skill0_25Pct,skill26_75Pct,skill76_150Pct,skill151_250Pct,skillGt250Pct,"
                    + "avgLatencyQuality,avgLatencyMs,latency10_30msPct,latency31_60msPct,latency61_100msPct,latency101_180msPct,latencyGt180msPct");

            for (SimulationReportEntity r : reports) {
                WaitTimeStatistics wt = r.getWaitTimeStatistics();
                SkillStatistics sk = r.getSkillStatistics();
                LatencyStatistics lt = r.getLatencyStatistics();

                pw.printf("%s,%s,%s,%s,%d,%d,%d,%d,%d,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                        r.getId(), r.getGameModeType(), r.getMatchmakingAlgorithmName(), r.getMatchesDate(),
                        r.getSkillWeight(), r.getWaitTimeWeight(), r.getLatencyWeight(),
                        r.getTotalQueueRequests(), r.getTotalMatches(),
                        r.getAbandonedMatchesRate(), r.getAverageMatchQuality(),
                        wt.getAverageWaitTimeQuality(), wt.getAverageWaitSeconds(),
                        wt.getWaitBetween0And5sPercentage(), wt.getWaitBetween6And30sPercentage(),
                        wt.getWaitBetween31And59sPercentage(), wt.getWaitMoreThan60sPercentage(),
                        sk.getAverageSkillQuality(), sk.getAverageSkillDiff(),
                        sk.getSkillDiffBetween0And25Percentage(), sk.getSkillDiffBetween26And75Percentage(),
                        sk.getSkillDiffBetween76And150Percentage(), sk.getSkillDiffBetween151And250Percentage(),
                        sk.getSkillDiffMoreThan250Percentage(),
                        lt.getAverageLatencyQuality(), lt.getAverageLatencyMs(),
                        lt.getLatencyBetween10And30msPercentage(), lt.getLatencyBetween31And60msPercentage(),
                        lt.getLatencyBetween61And100msPercentage(), lt.getLatencyBetween101And180msPercentage(),
                        lt.getLatencyMoreThan180msPercentage());
            }

            return ResponseEntity.ok("CSV exported successfully to " + filePath + " (" + reports.size() + " reports)");
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to export CSV: " + e.getMessage());
        }
    }


    @DeleteMapping
    public void deleteReport(@RequestParam UUID id) {
        service.deleteReport(id);
    }
}