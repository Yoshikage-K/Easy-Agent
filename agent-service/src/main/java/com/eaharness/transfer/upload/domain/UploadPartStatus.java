package com.eaharness.transfer.upload.domain;

public enum UploadPartStatus {
    UN_DOWNLOADED(0),
    DOWNLOADING(1),
    COMPLETED(2),
    PAUSED_OR_INTERRUPTED(3);

    private final int code;

    UploadPartStatus(int code) { this.code = code; }
    public int getCode() { return code; }

    public static UploadPartStatus fromCode(int code) {
        for (UploadPartStatus status : values()) {
            if (status.code == code) return status;
        }
        throw new IllegalArgumentException("Unknown part state: " + code);
    }
}
