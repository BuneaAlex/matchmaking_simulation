package com.master.matchmaking.model.simulation;

import com.master.matchmaking.model.enums.GameModeType;

import java.time.LocalDate;

public record ReportRequestDTO(GameModeType gameModeType, LocalDate date, double skillWeight, double waitTimeWeight,
                               double latencyWeight) {
}
