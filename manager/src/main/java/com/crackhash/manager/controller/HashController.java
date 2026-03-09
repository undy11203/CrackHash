package com.crackhash.manager.controller;

import com.crackhash.manager.dto.CrackRequest;
import com.crackhash.manager.dto.CrackResponse;
import com.crackhash.manager.dto.StatusResponse;
import com.crackhash.manager.service.HashCrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/hash")
@RequiredArgsConstructor
public class HashController {

    private final HashCrackService hashCrackService;

    @PostMapping("/crack")
    public ResponseEntity<CrackResponse> crackHash(@RequestBody CrackRequest request) {
        UUID requestId = hashCrackService.crack(request);
        return ResponseEntity.ok(new CrackResponse(requestId));
    }

    @GetMapping("/status")
    public ResponseEntity<StatusResponse> getStatus(@RequestParam UUID requestId) {
        return ResponseEntity.ok(hashCrackService.getStatus(requestId));
    }
}
