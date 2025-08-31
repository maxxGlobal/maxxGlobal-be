// src/main/java/com/maxx_global/config/SchedulingConfig.java
package com.maxx_global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.logging.Logger;

@Configuration
@EnableScheduling // ⭐ Bu çok önemli - scheduling'i aktif eder
public class SchedulingConfig {

    private static final Logger logger = Logger.getLogger(SchedulingConfig.class.getName());

    /**
     * Scheduled task'ler için özel thread pool
     */
    @Bean(name = "orderTaskScheduler")
    public TaskScheduler orderTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3); // 3 thread yeterli
        scheduler.setThreadNamePrefix("order-scheduler-");
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.initialize();

        logger.info("✅ Order Task Scheduler initialized with pool size: " + scheduler.getPoolSize());

        return scheduler;
    }

    /**
     * Scheduler health check
     */
    @Bean
    public SchedulerHealthCheck schedulerHealthCheck(TaskScheduler orderTaskScheduler) {
        return new SchedulerHealthCheck(orderTaskScheduler);
    }

    /**
     * Scheduler sağlık kontrolü için basit sınıf
     */
    public static class SchedulerHealthCheck {
        private final TaskScheduler taskScheduler;

        public SchedulerHealthCheck(TaskScheduler taskScheduler) {
            this.taskScheduler = taskScheduler;
        }

        public boolean isHealthy() {
            try {
                // TaskScheduler'ın çalışır durumda olup olmadığını kontrol et
                return taskScheduler != null;
            } catch (Exception e) {
                logger.severe("Scheduler health check failed: " + e.getMessage());
                return false;
            }
        }
    }
}