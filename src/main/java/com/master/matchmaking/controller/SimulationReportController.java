package com.master.matchmaking.controller;

import com.master.matchmaking.exceptions.ReportGenerationException;
import com.master.matchmaking.model.entity.SimulationReportEntity;
import com.master.matchmaking.model.enums.GameModeType;
import com.master.matchmaking.model.simulation.ReportRequestDTO;
import com.master.matchmaking.service.SimulationReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

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

}

