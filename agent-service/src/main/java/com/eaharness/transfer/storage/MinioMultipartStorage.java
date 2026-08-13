package com.eaharness.transfer.storage;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.minio.CreateMultipartUploadResponse;
import io.minio.MinioAsyncClient;
import io.minio.UploadPartResponse;
import io.minio.messages.Part;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MinioMultipartStorage implements MultipartStorage {
    private static final Multimap<String, String> EMPTY_HEADERS = ImmutableMultimap.of();
    private final MinioAsyncClient minioClient;

    public MinioMultipartStorage(MinioAsyncClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public String initiate(String bucket, String objectName) throws Exception {
        CreateMultipartUploadResponse response = minioClient.createMultipartUploadAsync(
                bucket, objectName, null, EMPTY_HEADERS, EMPTY_HEADERS).join();
        return response.result().uploadId();
    }

    @Override
    public UploadedPart uploadPart(String bucket, String objectName, String uploadId,
                                   int partNumber, InputStream data, long contentLength) throws Exception {
        UploadPartResponse response = minioClient.uploadPartAsync(
                bucket, objectName, uploadId, data, contentLength, null,
                partNumber, EMPTY_HEADERS, EMPTY_HEADERS).join();
        return new UploadedPart(partNumber, response.etag());
    }

    @Override
    public void complete(String bucket, String objectName, String uploadId,
                         List<UploadedPart> parts) throws Exception {
        Part[] minioParts = parts.stream()
                .sorted(Comparator.comparingInt(UploadedPart::partNumber))
                .map(part -> new Part(part.partNumber(), part.etag()))
                .toArray(Part[]::new);
        minioClient.completeMultipartUploadAsync(
                bucket, objectName, uploadId, null, minioParts,
                EMPTY_HEADERS, EMPTY_HEADERS).join();
    }

    @Override
    public void abort(String bucket, String objectName, String uploadId) throws Exception {
        minioClient.abortMultipartUploadAsync(
                bucket, objectName, uploadId, null,
                EMPTY_HEADERS, EMPTY_HEADERS).join();
    }
}
