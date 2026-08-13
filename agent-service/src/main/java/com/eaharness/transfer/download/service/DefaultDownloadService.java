package com.eaharness.transfer.download.service;

import com.eaharness.transfer.download.dto.DownloadRequest;
import com.eaharness.transfer.download.dto.DownloadUrlResponse;
import com.eaharness.transfer.download.exception.DownloadException;
import com.eaharness.transfer.download.storage.ObjectStorageReader;
import com.eaharness.transfer.download.storage.ObjectStorageReader.ObjectMetadata;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
public class DefaultDownloadService implements DownloadService {
    private static final Duration URL_EXPIRATION = Duration.ofMinutes(15);
    private final ObjectStorageReader storage;

    public DefaultDownloadService(ObjectStorageReader storage) {
        this.storage = storage;
    }

    @Override
    public DownloadUrlResponse createDownloadUrl(DownloadRequest request) {
        ObjectMetadata metadata = storage.stat(request.bucket(), request.objectName());
        return new DownloadUrlResponse(
                request.bucket(), request.objectName(), fileName(request.objectName()),
                metadata.size(), contentType(metadata),
                storage.presignedUrl(request.bucket(), request.objectName(), URL_EXPIRATION),
                URL_EXPIRATION.toSeconds());
    }

    @Override
    public ResponseEntity<StreamingResponseBody> download(
            String bucket, String objectName, String rangeHeader) {
        ObjectMetadata metadata = storage.stat(bucket, objectName);
        DownloadRange range = DownloadRange.parse(rangeHeader, metadata.size());
        long length = range.end() - range.start() + 1;
        InputStream input = storage.get(bucket, objectName, range.start(), length);
        StreamingResponseBody body = output -> {
            try (input) {
                input.transferTo(output);
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType(metadata)));
        headers.setContentLength(length);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(fileName(objectName), StandardCharsets.UTF_8).build());
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            headers.set(HttpHeaders.CONTENT_RANGE,
                    "bytes " + range.start() + "-" + range.end() + "/" + metadata.size());
            return new ResponseEntity<>(body, headers, HttpStatus.PARTIAL_CONTENT);
        }
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    private String fileName(String objectName) {
        int slash = objectName.lastIndexOf('/');
        return slash >= 0 ? objectName.substring(slash + 1) : objectName;
    }

    private String contentType(ObjectMetadata metadata) {
        return metadata.contentType() == null || metadata.contentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : metadata.contentType();
    }

    private record DownloadRange(long start, long end) {
        static DownloadRange parse(String header, long size) {
            if (header == null || header.isBlank()) return new DownloadRange(0, size - 1);
            if (!header.startsWith("bytes=") || header.indexOf(',') >= 0) {
                throw new DownloadException("Only one bytes range is supported");
            }
            String[] values = header.substring("bytes=".length()).split("-", -1);
            try {
                long start = Long.parseLong(values[0]);
                long end = values.length > 1 && !values[1].isBlank()
                        ? Long.parseLong(values[1]) : size - 1;
                if (start < 0 || start >= size || end < start) throw new NumberFormatException();
                return new DownloadRange(start, Math.min(end, size - 1));
            } catch (NumberFormatException exception) {
                throw new DownloadException("Invalid Range header: " + header, exception);
            }
        }
    }
}
