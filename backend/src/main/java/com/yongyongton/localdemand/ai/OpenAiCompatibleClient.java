package com.yongyongton.localdemand.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI Chat Completions(POST {baseUrl}/chat/completions)로 부르는 클라이언트.
 * JSON 모드(response_format: json_object)는 메시지에 "JSON" 낱말이 있어야 해서 지시문에 넣어 뒀어요.
 * 로그에는 오류 종류(HTTP 상태, 시간 초과)만 남기고 보낸 글·받은 글은 남기지 않아요.
 */
public class OpenAiCompatibleClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final String model;
    private final boolean jsonMode;
    private final String reasoningEffort;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleClient(String baseUrl, String apiKey, String model, Duration timeout,
                                  boolean jsonMode, String reasoningEffort, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.model = model;
        this.jsonMode = jsonMode;
        this.reasoningEffort = reasoningEffort;
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(String system, String user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            body.put("reasoning_effort", reasoningEffort);
        }

        byte[] response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8))
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientResponseException e) {
            log.warn("AI 호출 실패: HTTP {}", e.getStatusCode().value());
            throw new AiUnavailableException("HTTP_" + e.getStatusCode().value());
        } catch (RestClientException e) {
            // 응답 본문을 읽다가 시간이 다 되면 ResourceAccessException 이 아니라 다른 예외에 감싸져 와요
            if (e instanceof ResourceAccessException || causedByIo(e)) {
                log.warn("AI 호출 실패: 연결 실패 또는 시간 초과");
                throw new AiUnavailableException("TIMEOUT_OR_CONNECTION", e);
            }
            log.warn("AI 호출 실패: {}", e.getClass().getSimpleName());
            throw new AiUnavailableException("CLIENT_ERROR", e);
        }

        try {
            JsonNode content = objectMapper.readTree(response)
                    .path("choices").path(0).path("message").path("content");
            if (!content.isString() || content.asString().isBlank()) {
                throw new AiUnavailableException("EMPTY_RESPONSE");
            }
            return content.asString();
        } catch (JacksonException e) {
            log.warn("AI 응답을 읽지 못함: 형식 오류");
            throw new AiUnavailableException("BAD_RESPONSE", e);
        }
    }

    private static boolean causedByIo(Throwable e) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return true;
            }
        }
        return false;
    }
}
