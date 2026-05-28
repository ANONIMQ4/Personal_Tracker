package com.personal_tracker.app.rules.llm;

public class OpenAiApiException extends RuntimeException {

    public OpenAiApiException(String message) {
        super(message);
    }
}
