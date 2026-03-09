package com.crackhash.manager.config;

import org.springframework.stereotype.Component;

@Component
public class AlphabetConfig {
    
    /**
     * Алфавит для перебора: строчные латинские буквы (a-z) и цифры (0-9)
     */
    public static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    
    public String getAlphabet() {
        return ALPHABET;
    }
}
