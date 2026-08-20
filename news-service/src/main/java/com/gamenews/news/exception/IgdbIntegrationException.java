package com.gamenews.news.exception;

public class IgdbIntegrationException extends RuntimeException {
    public IgdbIntegrationException(String message) {
        super(message);
    }

    public IgdbIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
