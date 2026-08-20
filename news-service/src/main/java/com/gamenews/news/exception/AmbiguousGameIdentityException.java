package com.gamenews.news.exception;

public class AmbiguousGameIdentityException extends RuntimeException {
    public AmbiguousGameIdentityException(String message) {
        super(message);
    }
}
