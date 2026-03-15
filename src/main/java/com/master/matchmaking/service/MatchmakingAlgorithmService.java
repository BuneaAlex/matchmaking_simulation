package com.master.matchmaking.service;

import com.master.matchmaking.model.entity.MatchmakingAlgorithmEntity;
import com.master.matchmaking.model.enums.AlgorithmType;
import com.master.matchmaking.persistence.MatchmakingAlgorithmRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class MatchmakingAlgorithmService {
    private final MatchmakingAlgorithmRepository repository;

    public MatchmakingAlgorithmService(MatchmakingAlgorithmRepository repository) {
        this.repository = repository;
    }

    public MatchmakingAlgorithmEntity findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Algorithm not found: " + id));
    }


    public List<MatchmakingAlgorithmEntity> findAll() {
        return repository.findAll();
    }

}



