package com.yongyongton.localdemand.common.error;

import org.springframework.http.HttpStatus;

/**
 * API 명세 "1. 공통 규칙"의 오류 코드. 새 코드가 필요하면 명세에도 같이 적어요.
 * 서비스에서는 {@code throw new ApiException(ErrorCode.ALREADY_REQUESTED)}처럼 써요.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력한 값을 다시 확인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "이 기능을 쓸 수 있는 권한이 없어요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾는 정보가 없어요."),
    ALREADY_REQUESTED(HttpStatus.CONFLICT, "같은 동네에서 같은 업종은 한 번만 요청할 수 있어요."),
    ALREADY_ORDERED(HttpStatus.CONFLICT, "이 제안에는 이미 선주문했어요."),
    OFFER_CLOSED(HttpStatus.CONFLICT, "마감됐거나 취소된 제안이에요."),
    AI_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI가 응답하지 않아요. 업종을 직접 골라 주세요."),
    // 명세에는 없지만, 예상하지 못한 오류도 같은 모양으로 답하려고 둬요.
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 생겼어요. 잠시 뒤 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
