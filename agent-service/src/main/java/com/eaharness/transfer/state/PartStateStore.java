package com.eaharness.transfer.state;

import com.eaharness.transfer.upload.domain.UploadPartStatus;

public interface PartStateStore {
    UploadPartStatus get(String taskId, int partNumber);

    void set(String taskId, int partNumber, UploadPartStatus status);
}
