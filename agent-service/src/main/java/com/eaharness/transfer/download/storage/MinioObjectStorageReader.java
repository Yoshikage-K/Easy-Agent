package com.eaharness.transfer.download.storage;

import com.eaharness.transfer.download.exception.DownloadException;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class MinioObjectStorageReader implements ObjectStorageReader {
    private final MinioClient minio;

    public MinioObjectStorageReader(MinioClient minio) {
        this.minio = minio;
    }

    @Override
    public ObjectMetadata stat(String bucket, String objectName) {
        try {
            StatObjectResponse response = minio.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            return new ObjectMetadata(response.size(), response.contentType(), response.etag());
        } catch (Exception exception) {
            throw new DownloadException("Failed to read object metadata", exception);
        }
    }

    @Override
    public InputStream get(String bucket, String objectName, long offset, Long length) {
        try {
            GetObjectArgs.Builder builder = GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .offset(offset);
            if (length != null) builder.length(length);
            return minio.getObject(builder.build());
        } catch (Exception exception) {
            throw new DownloadException("Failed to open object stream", exception);
        }
    }

    @Override
    public String presignedUrl(String bucket, String objectName, Duration expiration) {
        try {
            return minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectName)
                    .expiry(Math.toIntExact(expiration.toSeconds()), TimeUnit.SECONDS)
                    .build());
        } catch (Exception exception) {
            throw new DownloadException("Failed to create download URL", exception);
        }
    }
}
