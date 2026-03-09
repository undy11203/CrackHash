package com.crackhash.manager.dto;

import lombok.Data;

@Data
public class CrackRequest {
    private String hash;
    private int maxLength;
}
