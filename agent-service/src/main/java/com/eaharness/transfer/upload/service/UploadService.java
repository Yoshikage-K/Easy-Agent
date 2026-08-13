package com.eaharness.transfer.upload.service;

import com.eaharness.transfer.upload.dto.CreateUploadRequest;
import com.eaharness.transfer.upload.dto.UploadResponse;
import com.eaharness.transfer.upload.dto.UploadStatusResponse;

public interface UploadService {
    UploadResponse create(CreateUploadRequest request);
    UploadResponse start(String taskId);
    UploadResponse pause(String taskId);
    UploadResponse resume(String taskId);
    UploadResponse retry(String taskId);
    UploadResponse cancel(String taskId);
    UploadStatusResponse status(String taskId);
}
