package com.eaharness.transfer.source;

public record RemoteFileMetadata(
        long contentLength,
        String etag,
        String contentType,
        String acceptRanges) {
}
