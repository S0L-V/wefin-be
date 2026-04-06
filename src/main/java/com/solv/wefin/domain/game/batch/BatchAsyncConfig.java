package com.solv.wefin.domain.game.batch;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class BatchAsyncConfig {

    @Bean("batchExecutor")
    public Executor batchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("batch-collect-");
        executor.setRejectedExecutionHandler((r, e) -> {
            throw new RuntimeException("배치가 이미 실행 중입니다");
        });
        executor.initialize();
        return executor;
    }
}
