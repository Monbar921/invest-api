package ru.invest.api.stock.supplier.ratelimiter.interceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import ru.invest.api.stock.supplier.ratelimiter.service.RateLimiterService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor {

    private final RateLimiterService rateLimiterService;

    @Around("execution(* ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc.InstrumentsServiceBlockingStub.*(..))")
    public Object interceptGrpcCall(final ProceedingJoinPoint joinPoint) throws Throwable {
        final String methodName = joinPoint.getSignature().getName();
        final Object[] args = joinPoint.getArgs();

        log.info("Intercepted gRPC call: {} with args: {}", methodName, args);

        final CompletableFuture<Object> future = rateLimiterService.executeWithRateLimit(() -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });

        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (final TimeoutException e) {
            log.error("Rate limited request timeout for method: {}", methodName);
            throw new RuntimeException("Request timed out due to rate limiting", e);
        } catch (final ExecutionException e) {
            throw e.getCause();
        }
    }
}