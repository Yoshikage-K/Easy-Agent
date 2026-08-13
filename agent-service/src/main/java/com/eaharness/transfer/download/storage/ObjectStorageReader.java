package com.eaharness.transfer.download.storage;

import java.io.InputStream;
import java.time.Duration;

public interface ObjectStorageReader {
    ObjectMetadata stat(String bucket, String objectName);

    InputStream get(String bucket, String objectName, long offset, Long length);

    String presignedUrl(String bucket, String objectName, Duration expiration);

    record ObjectMetadata(long size, String contentType, String etag) { }
}
