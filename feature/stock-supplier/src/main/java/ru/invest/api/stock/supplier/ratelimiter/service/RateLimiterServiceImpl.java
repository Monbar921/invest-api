package ru.invest.api.stock.supplier.ratelimiter.service;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class RateLimiterServiceImpl implements RateLimiterService {
    private static final int DEFAULT_VALUE = 0;

    private static final int REQUESTS_PER_MINUTE = 200;
    private static final int REQUESTS_PER_SECOND = 10;

    private static final int CORE_POOL_SIZE = 5;
    private static final int MAXIMUM_POOL_SIZE = 10;
    private static final long KEEP_ALIVE_TIME = 60L;

    private final AtomicInteger secondWindowCounter;
    private final AtomicInteger minuteWindowCounter;

    private final ScheduledExecutorService scheduler;
    private final BlockingQueue<Runnable> requestQueue;
    private final ExecutorService executorService;
    private final ReentrantLock lock = new ReentrantLock();

    public RateLimiterServiceImpl() {
        this.secondWindowCounter = new AtomicInteger(DEFAULT_VALUE);
        this.minuteWindowCounter = new AtomicInteger(DEFAULT_VALUE);

        this.scheduler = Executors.newScheduledThreadPool(2);
        this.requestQueue = new LinkedBlockingQueue<>(1000);

        this.executorService = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                requestQueue,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @PostConstruct
    public void init() {
        // Reset second counter every second
        scheduler.scheduleAtFixedRate(() -> {
            int requests = secondWindowCounter.getAndSet(DEFAULT_VALUE);
            log.info("Rate limit - last second: {} requests", requests);
        }, 1, 1, TimeUnit.SECONDS);

        // Reset minute counter every minute
        scheduler.scheduleAtFixedRate(() -> {
            int requests = minuteWindowCounter.getAndSet(DEFAULT_VALUE);
            log.info("Rate limit - last minute: {} requests", requests);
        }, 60, 60, TimeUnit.SECONDS);

        // Monitor queue size
        scheduler.scheduleAtFixedRate(() -> {
            int queueSize = requestQueue.size();
            if (queueSize > 100) {
                log.warn("Request queue size is high: {}", queueSize);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdown();
        executorService.shutdown();
    }

    public <T> CompletableFuture<T> executeWithRateLimit(final Callable<T> callable) {
        final CompletableFuture<T> future = new CompletableFuture<>();

        executorService.submit(() -> {
            try {
                waitForRateLimit();
                final T result = callable.call();
                future.complete(result);
            } catch (final Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private void waitForRateLimit() throws InterruptedException {
        while (true) {
            lock.lock();
            try {
                int currentSecond = secondWindowCounter.get();
                int currentMinute = minuteWindowCounter.get();

                if (currentSecond < REQUESTS_PER_SECOND && currentMinute < REQUESTS_PER_MINUTE) {
                    secondWindowCounter.incrementAndGet();
                    minuteWindowCounter.incrementAndGet();
                    log.debug("Request allowed. Second: {}/{}, Minute: {}/{}",
                            currentSecond + 1, REQUESTS_PER_SECOND,
                            currentMinute + 1, REQUESTS_PER_MINUTE);
                    return;
                }

                log.debug("Rate limit reached. Waiting... Second: {}/{}, Minute: {}/{}",
                        currentSecond, REQUESTS_PER_SECOND, currentMinute, REQUESTS_PER_MINUTE);
            } finally {
                lock.unlock();
            }

            Thread.sleep(50);
        }
    }
}
