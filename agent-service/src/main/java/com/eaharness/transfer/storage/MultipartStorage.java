package com.eaharness.transfer.storage;

import java.io.InputStream;
import java.util.List;

public interface MultipartStorage {
    String initiate(String bucket, String objectName) throws Exception;

    UploadedPart uploadPart(String bucket, String objectName, String uploadId,
                            int partNumber, InputStream data, long contentLength) throws Exception;

    void complete(String bucket, String objectName, String uploadId, List<UploadedPart> parts) throws Exception;

    void abort(String bucket, String objectName, String uploadId) throws Exception;

    record UploadedPart(int partNumber, String etag) { }
}
