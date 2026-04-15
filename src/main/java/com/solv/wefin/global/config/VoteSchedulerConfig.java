package com.solv.wefin.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class VoteSchedulerConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService voteScheduler() {
        return Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "vote-timer"));
    }
}
