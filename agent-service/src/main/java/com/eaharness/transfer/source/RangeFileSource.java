package com.eaharness.transfer.source;

import com.eaharness.transfer.upload.domain.UploadPart;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public interface RangeFileSource {
    RemoteFileMetadata head(String sourceUrl, Map<String, String> headers) throws IOException, InterruptedException;

    InputStream openRange(String sourceUrl, Map<String, String> headers, UploadPart part)
            throws IOException, InterruptedException;
}
