package com.eaharness.transfer.upload.service;

import com.eaharness.transfer.config.TransferProperties;
import com.eaharness.transfer.upload.domain.UploadPart;
import com.eaharness.transfer.upload.domain.UploadPartStatus;
import com.eaharness.transfer.upload.domain.UploadTask;
import com.eaharness.transfer.upload.domain.UploadTaskStatus;
import com.eaharness.transfer.upload.dto.CreateUploadRequest;
import com.eaharness.transfer.upload.dto.UploadResponse;
import com.eaharness.transfer.upload.dto.UploadStatusResponse;
import com.eaharness.transfer.upload.dto.UploadPartStatusResponse;
import com.eaharness.transfer.upload.exception.UploadException;
import com.eaharness.transfer.source.RangeFileSource;
import com.eaharness.transfer.source.RemoteFileMetadata;
import com.eaharness.transfer.state.PartStateStore;
import com.eaharness.transfer.storage.MultipartStorage;
import com.eaharness.transfer.util.DirectBufferInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class DefaultUploadService implements UploadService {
    private final RangeFileSource source;
    private final MultipartStorage storage;
    private final PartStateStore partStateStore;
    private final TransferProperties properties;
    private final ExecutorService partExecutor;
    private final ExecutorService coordinatorExecutor;
    private final Map<String, UploadTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, UploadRuntime> runtimes = new ConcurrentHashMap<>();

    public DefaultUploadService(RangeFileSource source, MultipartStorage storage,
                                  PartStateStore partStateStore, TransferProperties properties,
                                  @Qualifier("uploadPartExecutor") ExecutorService uploadPartExecutor,
                                  @Qualifier("uploadCoordinatorExecutor") ExecutorService uploadCoordinatorExecutor) {
        this.source = source;
        this.storage = storage;
        this.partStateStore = partStateStore;
        this.properties = properties;
        this.partExecutor = uploadPartExecutor;
        this.coordinatorExecutor = uploadCoordinatorExecutor;
    }

    @Override
    public UploadResponse create(CreateUploadRequest request) {
        try {
            RemoteFileMetadata metadata = source.head(request.sourceUrl(), request.headers());
            long partSize = properties.getPartSizeBytes();
            int partCount = calculatePartCount(metadata.contentLength(), partSize);
            String uploadId = storage.initiate(request.bucket(), request.objectName());
            String taskId = "upload_" + UUID.randomUUID().toString().replace("-", "");
            UploadTask task = new UploadTask(
                    taskId, request.sourceUrl(), request.headers(), request.bucket(), request.objectName(),
                    metadata.contentLength(), partSize, partCount, uploadId);
            tasks.put(taskId, task);
            return response(task);
        } catch (Exception exception) {
            throw new UploadException("Failed to create upload task", exception);
        }
    }

    @Override
    public UploadResponse start(String taskId) {
        UploadTask task = requireTask(taskId);
        UploadRuntime runtime = runtimes.computeIfAbsent(taskId, ignored -> new UploadRuntime());
        if (task.getStatus() == UploadTaskStatus.COMPLETED) return response(task);
        if (runtime.running.compareAndSet(false, true)) {
            runtime.pauseRequested.set(false);
            runtime.cancelRequested.set(false);
            task.setStatus(UploadTaskStatus.RUNNING);
            coordinatorExecutor.submit(() -> runTask(task, runtime));
        }
        return response(task);
    }

    @Override
    public UploadResponse pause(String taskId) {
        UploadTask task = requireTask(taskId);
        UploadRuntime runtime = runtimes.get(taskId);
        if (runtime == null || !runtime.running.get()) {
            task.setStatus(UploadTaskStatus.PAUSED);
            return response(task);
        }
        task.setStatus(UploadTaskStatus.PAUSING);
        runtime.pauseRequested.set(true);
        runtime.closeActiveStreams();
        return response(task);
    }

    @Override
    public UploadResponse resume(String taskId) {
        UploadTask task = requireTask(taskId);
        if (task.getStatus() != UploadTaskStatus.PAUSED
                && task.getStatus() != UploadTaskStatus.FAILED
                && task.getStatus() != UploadTaskStatus.PAUSING) {
            throw new UploadException("Task cannot be resumed from status " + task.getStatus());
        }
        return start(taskId);
    }

    @Override
    public UploadResponse retry(String taskId) {
        UploadTask task = requireTask(taskId);
        if (task.getStatus() != UploadTaskStatus.FAILED
                && task.getStatus() != UploadTaskStatus.PAUSED) {
            throw new UploadException("Task cannot be retried from status " + task.getStatus());
        }
        return start(taskId);
    }

    @Override
    public UploadResponse cancel(String taskId) {
        UploadTask task = requireTask(taskId);
        UploadRuntime runtime = runtimes.get(taskId);
        if (runtime != null) {
            runtime.cancelRequested.set(true);
            runtime.pauseRequested.set(true);
            runtime.closeActiveStreams();
        }
        task.setStatus(UploadTaskStatus.CANCELLED);
        try {
            storage.abort(task.getBucket(), task.getObjectName(), task.getUploadId());
        } catch (Exception exception) {
            throw new UploadException("Failed to abort multipart upload", exception);
        }
        return response(task);
    }

    @Override
    public UploadStatusResponse status(String taskId) {
        UploadTask task = requireTask(taskId);
        List<UploadPartStatusResponse> parts = new ArrayList<>();
        for (int partNumber = 1; partNumber <= task.getPartCount(); partNumber++) {
            UploadPart part = part(task, partNumber);
            parts.add(new UploadPartStatusResponse(
                    partNumber, part.start(), part.end(), partStateStore.get(taskId, partNumber).name()));
        }
        return new UploadStatusResponse(response(task), parts);
    }

    private void runTask(UploadTask task, UploadRuntime runtime) {
        try {
            boolean failed = false;
            int nextPartNumber = 1;
            int batchSize = properties.getWorkerCount();

            while (nextPartNumber <= task.getPartCount()
                    && !runtime.pauseRequested.get()
                    && !runtime.cancelRequested.get()) {
                CompletionService<Void> completion = new ExecutorCompletionService<>(partExecutor);
                int submitted = 0;
                int batchEnd = Math.min(task.getPartCount(), nextPartNumber + batchSize - 1);

                for (int partNumber = nextPartNumber; partNumber <= batchEnd; partNumber++) {
                    if (partStateStore.get(task.getTaskId(), partNumber) == UploadPartStatus.COMPLETED) {
                        continue;
                    }
                    int currentPart = partNumber;
                    completion.submit(() -> {
                        uploadPart(task, runtime, part(task, currentPart));
                        return null;
                    });
                    submitted++;
                }

                for (int index = 0; index < submitted; index++) {
                    try {
                        Future<Void> future = completion.take();
                        future.get();
                    } catch (Exception exception) {
                        failed = true;
                    }
                }

                if (failed || runtime.pauseRequested.get() || runtime.cancelRequested.get()) {
                    break;
                }
                nextPartNumber = batchEnd + 1;
            }

            if (runtime.cancelRequested.get()) {
                task.setStatus(UploadTaskStatus.CANCELLED);
            } else if (runtime.pauseRequested.get()) {
                task.setStatus(UploadTaskStatus.PAUSED);
            } else if (failed || !allPartsCompleted(task)) {
                task.setStatus(UploadTaskStatus.FAILED);
            } else {
                List<MultipartStorage.UploadedPart> parts = task.getUploadedParts().values().stream()
                        .map(part -> new MultipartStorage.UploadedPart(part.partNumber(), part.etag()))
                        .sorted(Comparator.comparingInt(MultipartStorage.UploadedPart::partNumber))
                        .toList();
                storage.complete(task.getBucket(), task.getObjectName(), task.getUploadId(), parts);
                task.setStatus(UploadTaskStatus.COMPLETED);
            }
        } catch (Exception exception) {
            task.setStatus(runtime.pauseRequested.get() ? UploadTaskStatus.PAUSED : UploadTaskStatus.FAILED);
        } finally {
            runtime.running.set(false);
        }
    }

    private void uploadPart(UploadTask task, UploadRuntime runtime, UploadPart part) {
        for (int attempt = 1; attempt <= properties.getMaxRetryAttempts(); attempt++) {
            if (runtime.pauseRequested.get() || runtime.cancelRequested.get()) {
                partStateStore.set(task.getTaskId(), part.partNumber(), UploadPartStatus.PAUSED_OR_INTERRUPTED);
                throw new UploadException("Part interrupted");
            }
            partStateStore.set(task.getTaskId(), part.partNumber(), UploadPartStatus.DOWNLOADING);
            InputStream remote = null;
            try {
                remote = source.openRange(task.getSourceUrl(), task.getSourceHeaders(), part);
                runtime.activeStreams.put(part.partNumber(), remote);
                try (DirectBufferInputStream buffered = new DirectBufferInputStream(
                        remote, properties.getBufferSizeBytes())) {
                    MultipartStorage.UploadedPart uploaded = storage.uploadPart(
                            task.getBucket(), task.getObjectName(), task.getUploadId(), part.partNumber(),
                            buffered, part.length());
                    task.getUploadedParts().put(part.partNumber(),
                            new UploadTask.UploadedPart(uploaded.partNumber(), uploaded.etag()));
                }
                partStateStore.set(task.getTaskId(), part.partNumber(), UploadPartStatus.COMPLETED);
                return;
            } catch (Exception exception) {
                partStateStore.set(task.getTaskId(), part.partNumber(), UploadPartStatus.PAUSED_OR_INTERRUPTED);
                if (runtime.pauseRequested.get() || runtime.cancelRequested.get()
                        || attempt == properties.getMaxRetryAttempts()) {
                    throw new UploadException("Part failed after " + attempt + " attempt(s)", exception);
                }
                sleepBeforeRetry();
            } finally {
                if (remote != null) runtime.activeStreams.remove(part.partNumber());
            }
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(properties.getRetryBackoffMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UploadException("Upload retry interrupted", exception);
        }
    }

    private boolean allPartsCompleted(UploadTask task) {
        for (int partNumber = 1; partNumber <= task.getPartCount(); partNumber++) {
            if (partStateStore.get(task.getTaskId(), partNumber) != UploadPartStatus.COMPLETED) return false;
        }
        return true;
    }

    private int calculatePartCount(long fileSize, long partSize) {
        long count = (fileSize - 1) / partSize + 1;
        if (count > properties.getMaxPartCount()) {
            throw new UploadException("File requires " + count + " parts, maximum is " + properties.getMaxPartCount());
        }
        return Math.toIntExact(count);
    }

    private UploadPart part(UploadTask task, int partNumber) {
        long start = (long) (partNumber - 1) * task.getPartSize();
        long end = Math.min(task.getFileSize() - 1, start + task.getPartSize() - 1);
        return new UploadPart(partNumber, start, end);
    }

    private UploadTask requireTask(String taskId) {
        UploadTask task = tasks.get(taskId);
        if (task == null) throw new UploadException("Upload task not found: " + taskId);
        return task;
    }

    private UploadResponse response(UploadTask task) {
        return new UploadResponse(task.getTaskId(), task.getStatus().name(), task.getFileSize(),
                task.getPartSize(), task.getPartCount(), task.getBucket(), task.getObjectName());
    }

    private static final class UploadRuntime {
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicBoolean pauseRequested = new AtomicBoolean();
        private final AtomicBoolean cancelRequested = new AtomicBoolean();
        private final Map<Integer, InputStream> activeStreams = new ConcurrentHashMap<>();

        private void closeActiveStreams() {
            activeStreams.values().forEach(stream -> {
                try { stream.close(); } catch (Exception ignored) { }
            });
        }
    }
}
