package com.yongyongton.localdemand.ai;

/**
 * AI를 쓸 수 없을 때(키 없음, 시간 초과, 오류 응답, 형식이 깨진 응답).
 * 메시지에는 오류 종류만 넣고 주민 문장·AI 응답 본문은 넣지 않아요(로그에 남지 않게).
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String reason) {
        super(reason);
    }

    public AiUnavailableException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
