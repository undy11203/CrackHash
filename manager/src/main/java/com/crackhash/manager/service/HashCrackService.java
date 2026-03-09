package com.crackhash.manager.service;

import com.crackhash.manager.config.AlphabetConfig;
import com.crackhash.manager.dto.CrackHashManagerRequest;
import com.crackhash.manager.dto.CrackHashWorkerResponse;
import com.crackhash.manager.dto.CrackRequest;
import com.crackhash.manager.dto.Status;
import com.crackhash.manager.dto.StatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class HashCrackService {

    private final RestTemplate restTemplate;
    private final AlphabetConfig alphabetConfig;

    @Value("${worker.url}")
    private String workerUrl;

    @Value("${worker.timeout}")
    private long timeoutSeconds;

    private final Map<UUID, StatusResponse> tasks = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> timeouts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public UUID crack(CrackRequest request) {
        UUID requestId = UUID.randomUUID();
        tasks.put(requestId, new StatusResponse(Status.IN_PROGRESS, null));

        CrackHashManagerRequest workerRequest = buildWorkerRequest(requestId, request, 0, 1);

        // Send task to worker asynchronously (worker returns 202 immediately)
        CompletableFuture.runAsync(() -> {
            try {
                restTemplate.exchange(
                        workerUrl + "/internal/api/worker/hash/crack/task",
                        HttpMethod.POST,
                        new HttpEntity<>(workerRequest),
                        Void.class
                );
            } catch (Exception e) {
                log.error("Failed to send task {} to worker: {}", requestId, e.getMessage());
                cancelTimeout(requestId);
                tasks.put(requestId, new StatusResponse(Status.ERROR, null));
            }
        });

        // Schedule timeout: if no callback arrives in time → ERROR
        ScheduledFuture<?> timeout = scheduler.schedule(() -> {
            tasks.put(requestId, new StatusResponse(Status.ERROR, null));
            timeouts.remove(requestId);
            log.warn("Task {} timed out after {}s", requestId, timeoutSeconds);
        }, timeoutSeconds, TimeUnit.SECONDS);
        timeouts.put(requestId, timeout);

        return requestId;
    }

    public void handleWorkerCallback(CrackHashWorkerResponse response) {
        UUID requestId = UUID.fromString(response.getRequestId());
        List<String> words = response.getAnswers().getWords();
        tasks.put(requestId, new StatusResponse(Status.READY, words));
        cancelTimeout(requestId);
        log.info("Task {} completed via callback, found: {}", requestId, words);
    }

    public StatusResponse getStatus(UUID requestId) {
        return tasks.getOrDefault(requestId, new StatusResponse(Status.IN_PROGRESS, null));
    }

    private void cancelTimeout(UUID requestId) {
        ScheduledFuture<?> timeout = timeouts.remove(requestId);
        if (timeout != null) {
            timeout.cancel(false);
        }
    }

    private CrackHashManagerRequest buildWorkerRequest(UUID requestId, CrackRequest request,
                                                        int partNumber, int partCount) {
        CrackHashManagerRequest req = new CrackHashManagerRequest();
        req.setRequestId(requestId.toString());
        req.setPartNumber(partNumber);
        req.setPartCount(partCount);
        req.setHash(request.getHash());
        req.setMaxLength(request.getMaxLength());

        CrackHashManagerRequest.Alphabet alphabet = new CrackHashManagerRequest.Alphabet();
        for (char c : alphabetConfig.getAlphabet().toCharArray()) {
            alphabet.getSymbols().add(String.valueOf(c));
        }
        req.setAlphabet(alphabet);

        return req;
    }
}
