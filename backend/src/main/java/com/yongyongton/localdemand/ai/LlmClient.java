package com.yongyongton.localdemand.ai;

/**
 * AI 모델 한 번 부르기. 모델 회사마다 다른 부분은 여기 구현체만 바꾸면 돼요.
 * 실패하면 {@link AiUnavailableException}.
 */
@FunctionalInterface
public interface LlmClient {

    /** 지시문(system)과 입력(user)을 보내고 모델이 쓴 글을 그대로 돌려줘요. */
    String complete(String system, String user);
}
