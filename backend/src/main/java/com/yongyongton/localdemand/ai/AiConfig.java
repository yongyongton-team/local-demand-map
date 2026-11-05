package com.yongyongton.localdemand.ai;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** AI_PROVIDER 값으로 어떤 AiWriter 를 쓸지 정해요. 값이 틀리거나 키가 비었으면 서버가 켜지지 않아요. */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public AiWriter aiWriter(AiProperties props, ObjectMapper objectMapper) {
        return switch (props.provider()) {
            case "fake" -> {
                log.info("AI: fake (AI 없이 낱말 맞추기, 요약은 기본 문장)");
                yield new FakeAiWriter();
            }
            case "openai" -> {
                require(props.apiKey(), "AI_API_KEY");
                require(props.baseUrl(), "AI_BASE_URL");
                require(props.model(), "AI_MODEL");
                log.info("AI: openai, 모델 {}, reasoning_effort {}", props.model(), props.reasoningEffort());
                yield new LlmAiWriter(new OpenAiCompatibleClient(props.baseUrl(), props.apiKey(), props.model(),
                        Duration.ofSeconds(props.timeoutSeconds()), props.jsonMode(), props.reasoningEffort(),
                        objectMapper), objectMapper);
            }
            default -> throw new IllegalStateException("AI_PROVIDER 는 fake 또는 openai 만 돼요.");
        };
    }

    private static void require(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("AI_PROVIDER=openai 이면 " + envName + " 를 넣어야 해요.");
        }
    }
}
