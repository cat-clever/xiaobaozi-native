package com.baozivideo.api;

public class ApiException extends Exception {
    public final boolean isServerBusy;

    public ApiException(String message) {
        this(message, false);
    }

    public ApiException(String message, boolean isServerBusy) {
        super(message);
        this.isServerBusy = isServerBusy;
    }
}
