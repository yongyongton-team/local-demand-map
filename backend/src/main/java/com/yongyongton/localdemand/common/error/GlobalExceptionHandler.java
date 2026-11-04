package com.yongyongton.localdemand.common.error;

import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 컨트롤러에서 나온 예외를 공통 오류 본문으로 바꿔요. 로그인 필터 단계의 401/403은 JsonSecurityErrorHandler 가 맡아요. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> handleApi(ApiException e) {
        return respond(e.code(), e.getMessage());
    }

    /** @Valid 검사에 걸림. 어느 칸이 왜 틀렸는지만 알려 주고, 보낸 값은 메시지에 다시 넣지 않아요. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> handleInvalid(MethodArgumentNotValidException e) {
        String fields = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return respond(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage() + " (" + fields + ")");
    }

    /** JSON이 깨졌거나, 숫자 자리에 글자가 오는 등 형식이 틀림. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorBody> handleUnreadable(Exception e) {
        return respond(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage());
    }

    /** 컨트롤러 안의 권한 검사(@PreAuthorize 등)에 걸림. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorBody> handleAccessDenied(AccessDeniedException e) {
        return respond(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> handleOther(Exception e) {
        // 스프링이 만든 4xx 오류(없는 주소, 빠진 파라미터, 안 되는 메서드 등)는 상태 코드를 그대로 써요.
        if (e instanceof ErrorResponse springError && springError.getStatusCode().is4xxClientError()) {
            ErrorCode code = springError.getStatusCode().value() == 404 ? ErrorCode.NOT_FOUND : ErrorCode.VALIDATION_ERROR;
            return ResponseEntity.status(springError.getStatusCode()).body(ErrorBody.of(code));
        }
        log.error("처리하지 못한 오류", e);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private static ResponseEntity<ErrorBody> respond(ErrorCode code, String message) {
        return ResponseEntity.status(code.status()).body(ErrorBody.of(code, message));
    }
}
