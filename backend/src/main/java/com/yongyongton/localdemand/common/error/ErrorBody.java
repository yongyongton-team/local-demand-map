package com.yongyongton.localdemand.common.error;

/** 오류 응답 본문. 항상 { "code": ..., "message": ... } 모양이에요. */
public record ErrorBody(String code, String message) {

    public static ErrorBody of(ErrorCode code) {
        return new ErrorBody(code.name(), code.defaultMessage());
    }

    public static ErrorBody of(ErrorCode code, String message) {
        return new ErrorBody(code.name(), message);
    }
}
