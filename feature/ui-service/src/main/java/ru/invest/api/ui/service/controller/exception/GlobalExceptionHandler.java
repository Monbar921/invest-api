package ru.invest.api.ui.service.controller.exception;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.grpc.Status;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.ttech.piapi.core.connector.exception.ServiceRuntimeException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GeneralNotFoundEntityException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleNotFound(final GeneralNotFoundEntityException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        final ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Not Found");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    // неизвестный путь: без этого обработчика его ловит общий handleGeneral и отвечает 500
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleNoResource(final NoResourceFoundException ex) {
        final ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Not Found");
        problem.setDetail("No endpoint %s %s".formatted(ex.getHttpMethod(), ex.getResourcePath()));
        return problem;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ProblemDetail handleMethodNotSupported(final HttpRequestMethodNotSupportedException ex) {
        final ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
        problem.setTitle("Method Not Allowed");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    // нет обязательного заголовка или параметра: например, From у maintenance-эндпоинтов, query у поиска
    @ExceptionHandler(ServletRequestBindingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleMissingRequestValue(final ServletRequestBindingException ex) {
        final ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Bad Request");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    // параметр не того типа: дата не в формате YYYY-MM-DD, неизвестный интервал
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleTypeMismatch(final MethodArgumentTypeMismatchException ex) {
        final ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Bad Request");
        problem.setDetail("Invalid value '%s' of parameter %s".formatted(ex.getValue(), ex.getName()));
        return problem;
    }

    // нарушены ограничения на параметры запроса (например, @Max на размер страницы)
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleConstraintViolation(final ConstraintViolationException ex) {
        log.warn("Invalid request: {}", ex.getMessage());
        final ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Bad Request");
        problem.setDetail(ex.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .collect(Collectors.joining("; ")));
        return problem;
    }

    @ExceptionHandler(GeneralUnprocessableEntityException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    public ProblemDetail handleUnprocessable(final GeneralUnprocessableEntityException ex) {
        log.warn("Unprocessable entity: {}", ex.getMessage());
        final ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        problem.setTitle("Unprocessable Entity");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    // клиентский лимит (rate limiter / bulkhead) не дождался свободного слота за отведённый таймаут
    @ExceptionHandler({RequestNotPermitted.class, BulkheadFullException.class})
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ProblemDetail handleRateLimit(final RuntimeException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return tooManyRequests();
    }

    @ExceptionHandler(CallNotPermittedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ProblemDetail handleCircuitOpen(final CallNotPermittedException ex) {
        log.warn("External API circuit breaker is open: {}", ex.getMessage());
        final ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setTitle("Service Unavailable");
        problem.setDetail("External API is temporarily unavailable, please retry later");
        return problem;
    }

    // ошибка Tinkoff Invest API после всех повторов SDK; статус ответа берётся из ProblemDetail
    @ExceptionHandler(ServiceRuntimeException.class)
    public ProblemDetail handleExternalApi(final ServiceRuntimeException ex) {
        if (ex.getErrorType() == Status.Code.RESOURCE_EXHAUSTED) {
            log.warn("External API rate limit exceeded, trackingId={}", ex.getTrackingId());
            return tooManyRequests();
        }

        log.error("External API error: code={}, description={}, trackingId={}",
                ex.getErrorType(), ex.getDescription(), ex.getTrackingId(), ex);
        final ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
        problem.setTitle("Bad Gateway");
        problem.setDetail("External API request failed");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ProblemDetail handleGeneral(final Exception ex) {
        log.error("Unexpected error", ex);
        final ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred");
        return problem;
    }

    private ProblemDetail tooManyRequests() {
        final ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        problem.setTitle("Too Many Requests");
        problem.setDetail("External API rate limit reached, please retry later");
        return problem;
    }
}
