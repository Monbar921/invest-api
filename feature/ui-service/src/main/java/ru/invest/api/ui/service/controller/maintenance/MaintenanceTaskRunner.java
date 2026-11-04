package ru.invest.api.ui.service.controller.maintenance;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Запускает ручную синхронизацию в фоне: полная синхронизация идёт десятки минут, держать на ней HTTP-запрос
 * нельзя - клиент отвалится по таймауту, а сама она от этого не остановится. Одна и та же задача одновременно
 * идёт только одна: повторный запуск, пока она не закончилась, - 409.
 */
@Slf4j
@Component
public class MaintenanceTaskRunner {
    private final ExecutorService executor = Executors.newCachedThreadPool(Thread.ofPlatform().name("maintenance-", 0).factory());
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    /**
     * @return 202 - задача запущена; 409 - такая уже идёт
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public ResponseEntity<ProblemDetail> start(final String task, final Runnable action) {
        if (!running.add(task)) {
            final ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
            problem.setTitle("Conflict");
            problem.setDetail("Maintenance task '%s' is already running".formatted(task));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
        }

        executor.execute(() -> {
            log.info("Maintenance task '{}' started", task);
            try {
                action.run();
                log.info("Maintenance task '{}' finished", task);
            } catch (final RuntimeException e) {
                log.error("Maintenance task '{}' failed", task, e);
            } finally {
                running.remove(task);
            }
        });
        return ResponseEntity.accepted().build();
    }

    @PreDestroy
    public void destroy() {
        executor.shutdownNow();
    }
}
