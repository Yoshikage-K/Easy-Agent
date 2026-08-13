package com.eaharness.transfer.upload.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CreateUploadRequest(
        @NotBlank String sourceUrl,
        @NotBlank String bucket,
        @NotBlank String objectName,
        Map<String, String> headers) {
    public CreateUploadRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
