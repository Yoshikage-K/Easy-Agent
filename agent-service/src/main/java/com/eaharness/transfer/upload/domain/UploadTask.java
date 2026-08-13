package com.eaharness.transfer.upload.domain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class UploadTask {
    private final String taskId;
    private final String sourceUrl;
    private final Map<String, String> sourceHeaders;
    private final String bucket;
    private final String objectName;
    private final long fileSize;
    private final long partSize;
    private final int partCount;
    private final String uploadId;
    private final Map<Integer, UploadedPart> uploadedParts = new ConcurrentHashMap<>();
    private final AtomicReference<UploadTaskStatus> status = new AtomicReference<>(UploadTaskStatus.CREATED);

    public UploadTask(String taskId, String sourceUrl, Map<String, String> sourceHeaders,
                        String bucket, String objectName, long fileSize, long partSize,
                        int partCount, String uploadId) {
        this.taskId = taskId;
        this.sourceUrl = sourceUrl;
        this.sourceHeaders = Map.copyOf(sourceHeaders);
        this.bucket = bucket;
        this.objectName = objectName;
        this.fileSize = fileSize;
        this.partSize = partSize;
        this.partCount = partCount;
        this.uploadId = uploadId;
    }

    public String getTaskId() { return taskId; }
    public String getSourceUrl() { return sourceUrl; }
    public Map<String, String> getSourceHeaders() { return sourceHeaders; }
    public String getBucket() { return bucket; }
    public String getObjectName() { return objectName; }
    public long getFileSize() { return fileSize; }
    public long getPartSize() { return partSize; }
    public int getPartCount() { return partCount; }
    public String getUploadId() { return uploadId; }
    public Map<Integer, UploadedPart> getUploadedParts() { return uploadedParts; }
    public UploadTaskStatus getStatus() { return status.get(); }
    public void setStatus(UploadTaskStatus value) { status.set(value); }

    public record UploadedPart(int partNumber, String etag) { }
}
