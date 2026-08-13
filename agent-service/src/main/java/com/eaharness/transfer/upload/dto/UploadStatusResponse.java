package com.eaharness.transfer.upload.dto;

import java.util.List;

public record UploadStatusResponse(UploadResponse task, List<UploadPartStatusResponse> parts) {
}
