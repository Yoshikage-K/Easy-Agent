package com.eaharness.transfer.upload.dto;

public record UploadResponse(
        String taskId,
        String status,
        long fileSize,
        long partSize,
        int partCount,
        String bucket,
        String objectName) {
}
