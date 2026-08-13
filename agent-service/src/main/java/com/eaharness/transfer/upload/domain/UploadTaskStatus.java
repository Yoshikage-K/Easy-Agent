package com.eaharness.transfer.upload.domain;

public enum UploadTaskStatus {
    CREATED,
    RUNNING,
    PAUSING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
