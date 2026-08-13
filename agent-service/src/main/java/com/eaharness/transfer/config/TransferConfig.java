package com.eaharness.transfer.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TransferProperties.class)
public class TransferConfig {
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService uploadPartExecutor(TransferProperties properties) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "upload-part-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(properties.getWorkerCount(), factory);
    }

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService uploadCoordinatorExecutor() {
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "upload-coordinator");
            thread.setDaemon(true);
            return thread;
        });
    }
}
