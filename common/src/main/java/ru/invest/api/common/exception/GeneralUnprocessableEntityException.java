package ru.invest.api.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
@Getter
public class GeneralUnprocessableEntityException extends RuntimeException {
    private final ExceptionErrorCode exceptionErrorCode;

    public GeneralUnprocessableEntityException(final ExceptionErrorCode exceptionErrorCode, final String message){
        super(message);
        this.exceptionErrorCode = exceptionErrorCode;
    }
}
