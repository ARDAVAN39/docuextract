package io.github.ardavan39.docuextract.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Documents are processed off the request thread. OCR and LLM calls are slow, so the pool is
 * small and bounded: a burst of uploads queues up instead of starving the machine.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String DOCUMENT_EXECUTOR = "documentExecutor";

    @Bean(DOCUMENT_EXECUTOR)
    public ThreadPoolTaskExecutor documentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("doc-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
