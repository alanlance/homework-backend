package com.example.demo.service;

public class RuleNotFoundException extends RuntimeException {

    public RuleNotFoundException(String apiKey) {
        super("Rate limit rule not found for apiKey: " + apiKey);
    }
}
