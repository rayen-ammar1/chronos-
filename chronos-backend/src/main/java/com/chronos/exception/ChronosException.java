package com.chronos.exception;

public class ChronosException extends RuntimeException {
    public ChronosException(String message) { super(message); }
    public ChronosException(String message, Throwable cause) { super(message, cause); }
}
