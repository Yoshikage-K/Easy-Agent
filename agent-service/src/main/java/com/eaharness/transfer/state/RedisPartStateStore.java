package com.eaharness.transfer.state;

import com.eaharness.transfer.upload.domain.UploadPartStatus;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisPartStateStore implements PartStateStore {
    private static final String KEY_PREFIX = "eaharness:download:parts:";
    private static final DefaultRedisScript<Long> WRITE_STATE = new DefaultRedisScript<>(
            "local offset = tonumber(ARGV[1]) "
                    + "local code = tonumber(ARGV[2]) "
                    + "local low = code % 2 "
                    + "local high = math.floor(code / 2) "
                    + "redis.call('SETBIT', KEYS[1], offset, low) "
                    + "redis.call('SETBIT', KEYS[1], offset + 1, high) "
                    + "return code", Long.class);

    private final StringRedisTemplate redis;

    public RedisPartStateStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public UploadPartStatus get(String taskId, int partNumber) {
        long offset = bitOffset(partNumber);
        Boolean low = redis.opsForValue().getBit(key(taskId), offset);
        Boolean high = redis.opsForValue().getBit(key(taskId), offset + 1);
        int code = (Boolean.TRUE.equals(low) ? 1 : 0) + (Boolean.TRUE.equals(high) ? 2 : 0);
        return UploadPartStatus.fromCode(code);
    }

    @Override
    public void set(String taskId, int partNumber, UploadPartStatus status) {
        redis.execute(WRITE_STATE, List.of(key(taskId)),
                String.valueOf(bitOffset(partNumber)), String.valueOf(status.getCode()));
    }

    private String key(String taskId) { return KEY_PREFIX + taskId; }
    private long bitOffset(int partNumber) { return (long) partNumber * 2L; }
}
