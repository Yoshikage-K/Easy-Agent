package com.eaharness.transfer.download.exception;

public class DownloadException extends RuntimeException {
    public DownloadException(String message) { super(message); }
    public DownloadException(String message, Throwable cause) { super(message, cause); }
}
