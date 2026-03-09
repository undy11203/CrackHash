package com.crackhash.worker.controller;

import com.crackhash.worker.dto.CrackHashManagerRequest;
import com.crackhash.worker.service.HashCrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/worker/hash/crack")
@RequiredArgsConstructor
public class WorkerController {

    private final HashCrackService hashCrackService;

    @PostMapping("/task")
    public ResponseEntity<Void> handleCrackTask(@RequestBody CrackHashManagerRequest request) {
        hashCrackService.processTaskAsync(request);
        return ResponseEntity.accepted().build();
    }
}
