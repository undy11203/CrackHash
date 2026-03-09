package com.crackhash.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class StatusResponse {
    private Status status;
    private List<String> data;
}
