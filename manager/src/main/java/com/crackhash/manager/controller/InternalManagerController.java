package com.crackhash.manager.controller;

import com.crackhash.manager.dto.CrackHashWorkerResponse;
import com.crackhash.manager.service.HashCrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/manager/hash/crack")
@RequiredArgsConstructor
public class InternalManagerController {

    private final HashCrackService hashCrackService;

    @PatchMapping("/request")
    public ResponseEntity<Void> handleWorkerCallback(@RequestBody CrackHashWorkerResponse response) {
        hashCrackService.handleWorkerCallback(response);
        return ResponseEntity.ok().build();
    }
}
