package ru.invest.api.tinkoff.supplier.ratelimiter.service.impl;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.invest.api.common.exception.RateLimiterException;
import ru.invest.api.tinkoff.supplier.ratelimiter.config.RateLimiterProperties;
import ru.invest.api.tinkoff.supplier.ratelimiter.service.RateLimiterService;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterServiceImpl implements RateLimiterService {
    private static final int DEFAULT_VALUE = 0;
    private static final int CORE_POOL_SIZE = 5;
    private static final int MAXIMUM_POOL_SIZE = 10;
    private static final long KEEP_ALIVE_TIME = 60L;

    private final RateLimiterProperties properties;

    private final AtomicInteger secondWindowCounter = new AtomicInteger(DEFAULT_VALUE);
    private final AtomicInteger minuteWindowCounter = new AtomicInteger(DEFAULT_VALUE);

    // Condition-based ожидание: потоки спят до сигнала от scheduler
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notLimited = lock.newCondition();

    private ScheduledExecutorService scheduler;
    private BlockingQueue<Runnable> requestQueue;
    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        requestQueue = new LinkedBlockingQueue<>(1000);
        executorService = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                requestQueue,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        scheduler = Executors.newScheduledThreadPool(2);

        // Сброс счётчика секунды — под локом, будим ожидающие потоки
        scheduler.scheduleAtFixedRate(() -> {
            lock.lock();
            try {
                int requests = secondWindowCounter.getAndSet(DEFAULT_VALUE);
                log.info("Rate limit - last second: {} requests", requests);
                notLimited.signalAll();
            } finally {
                lock.unlock();
            }
        }, 1, 1, TimeUnit.SECONDS);

        // Сброс счётчика минуты — под локом
        scheduler.scheduleAtFixedRate(() -> {
            lock.lock();
            try {
                int requests = minuteWindowCounter.getAndSet(DEFAULT_VALUE);
                log.info("Rate limit - last minute: {} requests", requests);
                notLimited.signalAll();
            } finally {
                lock.unlock();
            }
        }, 60, 60, TimeUnit.SECONDS);

        // Мониторинг размера очереди
        scheduler.scheduleAtFixedRate(() -> {
            int queueSize = requestQueue.size();
            if (queueSize > 100) {
                log.warn("Request queue size is high: {}", queueSize);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Override
    public <T> CompletableFuture<T> executeWithRateLimitAsync(final Callable<T> callable) {
        final CompletableFuture<T> future = new CompletableFuture<>();

        executorService.submit(() -> {
            try {
                waitForRateLimit();
                future.complete(callable.call());
            } catch (final Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    @Override
    public <T> T executeWithRateLimitAsyncAndGet(final Callable<T> callable) {
        try {
            return executeWithRateLimitAsync(callable).get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RateLimiterException("Interrupted while waiting for result", e);
        } catch (final ExecutionException e) {
            throw new RateLimiterException("Execution failed", e);
        }
    }

    @Override
    public <T> T executeWithRateLimitSync(final Supplier<T> supplier) {
        waitForRateLimit();
        return supplier.get();
    }

    /**
     * Ожидает освобождения лимита.
     * Использует Condition.await вместо спин-луппа: поток засыпает и просыпается
     * только когда scheduler сбрасывает счётчик и вызывает signalAll().
     * Весь доступ к счётчикам — под одним локом, что исключает гонку данных.
     */
    private void waitForRateLimit() {
        final long deadline = System.currentTimeMillis() + properties.getTimeoutMs();

        lock.lock();
        try {
            while (secondWindowCounter.get() >= properties.getRequestsPerSecond()
                    || minuteWindowCounter.get() >= properties.getRequestsPerMinute()) {

                final long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new RateLimiterException(
                            "Rate limit timeout exceeded after " + properties.getTimeoutMs() + "ms");
                }

                log.debug("Rate limit reached. Waiting up to {}ms. Second: {}/{}, Minute: {}/{}",
                        remaining,
                        secondWindowCounter.get(), properties.getRequestsPerSecond(),
                        minuteWindowCounter.get(), properties.getRequestsPerMinute());

                notLimited.await(remaining, TimeUnit.MILLISECONDS);
            }

            secondWindowCounter.incrementAndGet();
            minuteWindowCounter.incrementAndGet();

            log.debug("Request allowed. Second: {}/{}, Minute: {}/{}",
                    secondWindowCounter.get(), properties.getRequestsPerSecond(),
                    minuteWindowCounter.get(), properties.getRequestsPerMinute());

        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RateLimiterException("Interrupted while waiting for rate limit");
        } finally {
            lock.unlock();
        }
    }
}
