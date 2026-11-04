package com.yongyongton.localdemand.common.error;

/** 서비스에서 던지면 GlobalExceptionHandler 가 ErrorCode 의 상태 코드와 공통 오류 본문으로 바꿔요. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    /** 화면에 보일 문장을 바꾸고 싶을 때. 주민이 쓴 문장 같은 입력값은 넣지 않아요. */
    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
