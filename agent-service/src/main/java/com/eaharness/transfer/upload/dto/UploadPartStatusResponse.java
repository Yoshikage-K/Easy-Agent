package com.eaharness.transfer.upload.dto;

public record UploadPartStatusResponse(int partNumber, long start, long end, String status) {
}
