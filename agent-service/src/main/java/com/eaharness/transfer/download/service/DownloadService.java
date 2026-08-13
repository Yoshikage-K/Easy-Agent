package com.eaharness.transfer.download.service;

import com.eaharness.transfer.download.dto.DownloadRequest;
import com.eaharness.transfer.download.dto.DownloadUrlResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public interface DownloadService {
    DownloadUrlResponse createDownloadUrl(DownloadRequest request);

    ResponseEntity<StreamingResponseBody> download(
            String bucket, String objectName, String range);
}
