package com.eaharness.transfer.upload.exception;

public class UploadException extends RuntimeException {
    public UploadException(String message) { super(message); }
    public UploadException(String message, Throwable cause) { super(message, cause); }
}
