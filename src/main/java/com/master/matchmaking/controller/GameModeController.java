package com.master.matchmaking.controller;

import com.master.matchmaking.model.entity.GameModeEntity;
import com.master.matchmaking.service.GameModeService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/game-modes")
@CrossOrigin(origins = "http://localhost:3000")
public class GameModeController {
    private final GameModeService service;

    public GameModeController(GameModeService service) {
        this.service = service;
    }

    @GetMapping
    public List<GameModeEntity> getAll() {
        return service.findAll();
    }
}

