package com.crackhash.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class CrackResponse {
    private UUID requestId;
}
