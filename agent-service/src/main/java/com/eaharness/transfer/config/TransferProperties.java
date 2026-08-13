package com.eaharness.transfer.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eaharness.transfer")
public class TransferProperties {
    public static final int DEFAULT_WORKER_COUNT = 8;
    public static final long DEFAULT_PART_SIZE_BYTES = 128L * 1024L * 1024L;
    public static final int DEFAULT_MAX_PART_COUNT = 10_000;
    public static final int DEFAULT_MAX_RETRY_ATTEMPTS = 3;

    private int workerCount = DEFAULT_WORKER_COUNT;
    private long partSizeBytes = DEFAULT_PART_SIZE_BYTES;
    private int bufferSizeBytes = 8 * 1024 * 1024;
    private int maxPartCount = DEFAULT_MAX_PART_COUNT;
    private int maxRetryAttempts = DEFAULT_MAX_RETRY_ATTEMPTS;
    private long retryBackoffMillis = 1000;
    private long requestTimeoutSeconds = 120;
    private Minio minio = new Minio();

    public int getWorkerCount() { return workerCount; }
    public void setWorkerCount(int workerCount) { this.workerCount = workerCount; }
    public long getPartSizeBytes() { return partSizeBytes; }
    public void setPartSizeBytes(long partSizeBytes) { this.partSizeBytes = partSizeBytes; }
    public int getBufferSizeBytes() { return bufferSizeBytes; }
    public void setBufferSizeBytes(int bufferSizeBytes) { this.bufferSizeBytes = bufferSizeBytes; }
    public int getMaxPartCount() { return maxPartCount; }
    public void setMaxPartCount(int maxPartCount) { this.maxPartCount = maxPartCount; }
    public int getMaxRetryAttempts() { return maxRetryAttempts; }
    public void setMaxRetryAttempts(int maxRetryAttempts) { this.maxRetryAttempts = maxRetryAttempts; }
    public long getRetryBackoffMillis() { return retryBackoffMillis; }
    public void setRetryBackoffMillis(long retryBackoffMillis) { this.retryBackoffMillis = retryBackoffMillis; }
    public long getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(long requestTimeoutSeconds) { this.requestTimeoutSeconds = requestTimeoutSeconds; }
    public Minio getMinio() { return minio; }
    public void setMinio(Minio minio) { this.minio = minio; }

    public Duration requestTimeout() { return Duration.ofSeconds(requestTimeoutSeconds); }

    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    }
}
