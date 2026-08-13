package com.eaharness.transfer.download.dto;

public record DownloadUrlResponse(
        String bucket,
        String objectName,
        String fileName,
        long fileSize,
        String contentType,
        String downloadUrl,
        long expiresInSeconds) {
}
