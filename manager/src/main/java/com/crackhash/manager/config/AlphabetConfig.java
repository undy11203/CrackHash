package com.crackhash.manager.config;

import org.springframework.stereotype.Component;

@Component
public class AlphabetConfig {
    
    public static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    
    public String getAlphabet() {
        return ALPHABET;
    }
}
