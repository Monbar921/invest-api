package ru.invest.api.tinkof.stock.supplier.ratelimiter.service;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface RateLimiterService {
    <T> CompletableFuture<T> executeWithRateLimitAsync(Callable<T> callable);

    <T> T executeWithRateLimitAsyncAndGet(Callable<T> callable);

    <T> T executeWithRateLimitSync(Supplier<T> supplier);
}
