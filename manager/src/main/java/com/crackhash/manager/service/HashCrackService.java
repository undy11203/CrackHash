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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class HashCrackService {

    private final RestTemplate restTemplate;
    private final AlphabetConfig alphabetConfig;

    @Value("${workers.urls}")
    private List<String> workerUrls;

    @Value("${workers.timeout}")
    private long timeoutSeconds;

    private final Map<UUID, StatusResponse> tasks = new ConcurrentHashMap<>();
    private final Map<UUID, TaskState> taskStates = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> timeouts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public UUID crack(CrackRequest request) {
        UUID requestId = UUID.randomUUID();
        int partCount = workerUrls.size();

        tasks.put(requestId, new StatusResponse(Status.IN_PROGRESS, null));
        taskStates.put(requestId, new TaskState(partCount));

        // Send each worker its own part
        for (int partNumber = 0; partNumber < partCount; partNumber++) {
            String workerUrl = workerUrls.get(partNumber);
            CrackHashManagerRequest workerRequest = buildWorkerRequest(requestId, request, partNumber, partCount);

            try {
                restTemplate.exchange(
                        workerUrl + "/internal/api/worker/hash/crack/task",
                        HttpMethod.POST,
                        new HttpEntity<>(workerRequest),
                        Void.class
                );
            } catch (Exception e) {
                log.error("Failed to send task {} to worker {}: {}", requestId, workerUrl, e.getMessage());
                taskStates.remove(requestId);
                cancelTimeout(requestId);
                tasks.put(requestId, new StatusResponse(Status.ERROR, null));
                return requestId;
            }
        }

        // Schedule timeout: if not all callbacks arrive in time → ERROR
        ScheduledFuture<?> timeout = scheduler.schedule(() -> {
            tasks.put(requestId, new StatusResponse(Status.ERROR, null));
            taskStates.remove(requestId);
            timeouts.remove(requestId);
            log.warn("Task {} timed out after {}s", requestId, timeoutSeconds);
        }, timeoutSeconds, TimeUnit.SECONDS);
        timeouts.put(requestId, timeout);

        return requestId;
    }

    public void handleWorkerCallback(CrackHashWorkerResponse response) {
        UUID requestId = UUID.fromString(response.getRequestId());
        TaskState state = taskStates.get(requestId);
        if (state == null) {
            log.warn("Received callback for unknown or timed-out task {}", requestId);
            return;
        }

        state.addWords(response.getAnswers().getWords());
        int remaining = state.countDown();
        log.info("Task {}: received part {}, remaining={}", requestId, response.getPartNumber(), remaining);

        if (remaining == 0) {
            tasks.put(requestId, new StatusResponse(Status.READY, state.getWords()));
            taskStates.remove(requestId);
            cancelTimeout(requestId);
            log.info("Task {} fully completed, found: {}", requestId, state.getWords());
        }
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

    private static class TaskState {
        private final AtomicInteger remaining;
        private final List<String> words = Collections.synchronizedList(new ArrayList<>());

        TaskState(int partCount) {
            this.remaining = new AtomicInteger(partCount);
        }

        void addWords(List<String> newWords) {
            words.addAll(newWords);
        }

        /** Returns remaining part count after decrement */
        int countDown() {
            return remaining.decrementAndGet();
        }

        List<String> getWords() {
            return words;
        }
    }
}
