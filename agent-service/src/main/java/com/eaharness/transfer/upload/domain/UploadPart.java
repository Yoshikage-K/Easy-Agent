package com.eaharness.transfer.upload.domain;

public record UploadPart(int partNumber, long start, long end) {
    public long length() { return end - start + 1; }
}
