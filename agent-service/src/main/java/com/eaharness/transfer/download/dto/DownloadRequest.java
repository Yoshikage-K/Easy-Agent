package com.eaharness.transfer.download.dto;

import jakarta.validation.constraints.NotBlank;

public record DownloadRequest(
        @NotBlank String bucket,
        @NotBlank String objectName) {
}
