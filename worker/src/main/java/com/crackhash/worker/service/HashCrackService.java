package com.crackhash.worker.service;

import com.crackhash.worker.dto.CrackHashManagerRequest;
import com.crackhash.worker.dto.CrackHashWorkerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.paukov.combinatorics3.Generator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class HashCrackService {

    private final RestTemplate restTemplate;

    @Value("${manager.url}")
    private String managerUrl;

    public void processTaskAsync(CrackHashManagerRequest request) {
        CompletableFuture.runAsync(() -> {
            try {
                List<String> results = findMatches(request);

                CrackHashWorkerResponse.Answers answers = new CrackHashWorkerResponse.Answers();
                answers.getWords().addAll(results);

                CrackHashWorkerResponse response = new CrackHashWorkerResponse();
                response.setRequestId(request.getRequestId());
                response.setPartNumber(request.getPartNumber());
                response.setAnswers(answers);

                restTemplate.exchange(
                        managerUrl + "/internal/api/manager/hash/crack/request",
                        HttpMethod.PATCH,
                        new HttpEntity<>(response),
                        Void.class
                );
                log.info("Sent callback for task {}, found: {}", request.getRequestId(), results);
            } catch (Exception e) {
                log.error("Failed to process task {}: {}", request.getRequestId(), e.getMessage());
            }
        });
    }

    private List<String> findMatches(CrackHashManagerRequest request) {
        String targetHash = request.getHash().toLowerCase();
        int maxLength = request.getMaxLength();
        int partNumber = request.getPartNumber();
        int partCount = request.getPartCount();
        List<String> symbols = request.getAlphabet().getSymbols();
        int alphabetSize = symbols.size();

        log.info("Processing task {}: hash={}, maxLength={}, part={}/{}",
                request.getRequestId(), targetHash, maxLength, partNumber, partCount);

        long totalCount = 0;
        for (int len = 1; len <= maxLength; len++) {
            totalCount += pow(alphabetSize, len);
        }

        long globalStart = totalCount * partNumber / partCount;
        long globalEnd = totalCount * (partNumber + 1) / partCount;

        List<String> results = new ArrayList<>();
        long offset = 0; 
        for (int len = 1; len <= maxLength; len++) {
            long countForLen = pow(alphabetSize, len);

            if (globalEnd <= offset || globalStart >= offset + countForLen) {
                offset += countForLen;
                continue;
            }

            long localStart = Math.max(globalStart, offset) - offset;
            long localEnd = Math.min(globalEnd, offset + countForLen) - offset;

            long idx = 0;
            for (List<String> perm : Generator.permutation(symbols).withRepetitions(len)) {
                if (idx >= localEnd) break;
                if (idx >= localStart) {
                    String candidate = String.join("", perm);
                    if (md5(candidate).equals(targetHash)) {
                        log.info("Found match for {}: {}", targetHash, candidate);
                        results.add(candidate);
                    }
                }
                idx++;
            }

            offset += countForLen;
        }

        return results;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private long pow(long base, int exp) {
        long result = 1;
        for (int i = 0; i < exp; i++) {
            result *= base;
        }
        return result;
    }
}
