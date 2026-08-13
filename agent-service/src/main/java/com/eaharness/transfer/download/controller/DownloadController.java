package com.eaharness.transfer.download.controller;

import com.eaharness.transfer.download.dto.DownloadRequest;
import com.eaharness.transfer.download.dto.DownloadUrlResponse;
import com.eaharness.transfer.download.service.DownloadService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/downloads")
public class DownloadController {
    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @PostMapping("/url")
    public DownloadUrlResponse createUrl(@Valid @RequestBody DownloadRequest request) {
        return downloadService.createDownloadUrl(request);
    }

    @GetMapping
    public ResponseEntity<StreamingResponseBody> download(
            @RequestParam String bucket,
            @RequestParam String objectName,
            @RequestHeader(value = "Range", required = false) String range) {
        return downloadService.download(bucket, objectName, range);
    }

    @GetMapping("/{bucket}/{objectName:.+}")
    public ResponseEntity<StreamingResponseBody> downloadByPath(
            @PathVariable String bucket,
            @PathVariable String objectName,
            @RequestHeader(value = "Range", required = false) String range) {
        return downloadService.download(bucket, objectName, range);
    }
}
