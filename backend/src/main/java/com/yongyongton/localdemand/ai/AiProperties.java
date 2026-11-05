package com.yongyongton.localdemand.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml 의 app.ai 설정. 값은 환경변수로 넣어요(README 환경변수 표).
 *
 * @param provider       fake(키 없이 낱말 맞추기, 기본값) 또는 openai(OpenAI 형식 API)
 * @param apiKey         AI 키. 서버에만 두고 브라우저로 보내지 않아요
 * @param baseUrl        API 주소. 예: https://api.openai.com/v1 (뒤에 /chat/completions 를 붙여 불러요)
 * @param model          모델 이름
 * @param timeoutSeconds 응답 기다리는 시간(초)
 * @param jsonMode       response_format: json_object 를 보낼지. 모델이 이 값을 거부하면 false
 * @param reasoningEffort reasoning_effort 값. 비우면 보내지 않아요. 예: none(생각 없이 바로 답함, 빠르고 쌈)
 */
@ConfigurationProperties("app.ai")
public record AiProperties(
        @DefaultValue("fake") String provider,
        String apiKey,
        String baseUrl,
        String model,
        @DefaultValue("15") int timeoutSeconds,
        @DefaultValue("true") boolean jsonMode,
        String reasoningEffort) {
}
