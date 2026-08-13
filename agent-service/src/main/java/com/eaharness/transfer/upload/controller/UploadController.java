package com.eaharness.transfer.upload.controller;

import com.eaharness.transfer.upload.dto.CreateUploadRequest;
import com.eaharness.transfer.upload.dto.UploadResponse;
import com.eaharness.transfer.upload.dto.UploadStatusResponse;
import com.eaharness.transfer.upload.service.UploadService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {
    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping
    public UploadResponse create(@Valid @RequestBody CreateUploadRequest request) {
        return uploadService.create(request);
    }

    @PostMapping("/{taskId}/start")
    public UploadResponse start(@PathVariable String taskId) { return uploadService.start(taskId); }

    @PostMapping("/{taskId}/pause")
    public UploadResponse pause(@PathVariable String taskId) { return uploadService.pause(taskId); }

    @PostMapping("/{taskId}/resume")
    public UploadResponse resume(@PathVariable String taskId) { return uploadService.resume(taskId); }

    @PostMapping("/{taskId}/retry")
    public UploadResponse retry(@PathVariable String taskId) { return uploadService.retry(taskId); }

    @PostMapping("/{taskId}/cancel")
    public UploadResponse cancel(@PathVariable String taskId) { return uploadService.cancel(taskId); }

    @GetMapping("/{taskId}")
    public UploadStatusResponse status(@PathVariable String taskId) { return uploadService.status(taskId); }
}
