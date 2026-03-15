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
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    @Value("${workers.urls}")
    private List<String> workerUrls;

    @Value("${workers.timeout}")
    private long timeoutSeconds;

    private final Map<UUID, TaskState> taskStates = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> timeouts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public UUID crack(CrackRequest request) {
        UUID requestId = UUID.randomUUID();
        int partCount = workerUrls.size();

        TaskState state = new TaskState(partCount);
        taskStates.put(requestId, state);

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
                state.status = Status.ERROR;
                return requestId;
            }
        }

        ScheduledFuture<?> timeout = scheduler.schedule(() -> {
            TaskState s = taskStates.get(requestId);
            synchronized (s) {
                if (s.status != Status.IN_PROGRESS) return;
                s.status = Status.ERROR;
            }
            timeouts.remove(requestId);
            log.warn("Task {} timed out after {}s", requestId, timeoutSeconds);
        }, timeoutSeconds, TimeUnit.SECONDS);
        timeouts.put(requestId, timeout);

        return requestId;
    }

    public void handleWorkerCallback(CrackHashWorkerResponse response) {
        UUID requestId = UUID.fromString(response.getRequestId());
        int partNumber = response.getPartNumber();

        TaskState state = taskStates.get(requestId);
        if (state == null) {
            log.warn("Received callback for unknown task {}", requestId);
            return;
        }

        synchronized (state) {
            if (state.status != Status.IN_PROGRESS) {
                log.warn("Task {}: ignoring part {} — task already {}", requestId, partNumber, state.status);
                return;
            }
            state.words.addAll(response.getAnswers().getWords());
            int remaining = state.partCount - ++state.receivedParts;
            log.info("Task {}: received part {}, remaining={}", requestId, partNumber, remaining);

            if (remaining == 0) {
                state.status = Status.READY;
                cancelTimeout(requestId);
                log.info("Task {} fully completed, found: {}", requestId, state.words);
            }
        }
    }

    public StatusResponse getStatus(UUID requestId) {
        TaskState state = taskStates.get(requestId);
        if (state == null) { //хз что возвращать
            return new StatusResponse(Status.IN_PROGRESS, null);
        }
        synchronized (state) {
            return new StatusResponse(state.status,
                    state.status == Status.READY ? new ArrayList<>(state.words) : null);
        }
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
        Status status = Status.IN_PROGRESS;
        int partCount;
        int receivedParts = 0;
        List<String> words = new ArrayList<>();

        TaskState(int partCount) {
            this.partCount = partCount;
        }
    }
}
