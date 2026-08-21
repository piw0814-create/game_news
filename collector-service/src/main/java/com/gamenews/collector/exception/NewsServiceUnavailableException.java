package com.gamenews.collector.exception;

public class NewsServiceUnavailableException extends RuntimeException {

    public NewsServiceUnavailableException(String message) {
        super(message);
    }

    public NewsServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
