package com.yongyongton.localdemand.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 테스트 안에 띄운 가짜 HTTP 서버로 요청 모양과 오류 처리를 확인해요(실제 AI API는 부르지 않아요). */
class OpenAiCompatibleClientTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "";
    private volatile long delayMs = 0;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private OpenAiCompatibleClient client(Duration timeout, boolean jsonMode) {
        return client(timeout, jsonMode, "none");
    }

    private OpenAiCompatibleClient client(Duration timeout, boolean jsonMode, String reasoningEffort) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
        return new OpenAiCompatibleClient(baseUrl, "test-key", "test-model", timeout, jsonMode, reasoningEffort,
                objectMapper);
    }

    private static String answer(String content) {
        return "{\"choices\": [{\"message\": {\"role\": \"assistant\", \"content\": "
                + JsonMapper.builder().build().writeValueAsString(content) + "}}]}";
    }

    @Test
    @DisplayName("OpenAI 형식으로 보내고 choices[0].message.content 를 돌려줘요")
    void requestAndResponse() {
        responseBody = answer("{\"text\": \"구성동에 3명이 원해요.\"}");

        String content = client(Duration.ofSeconds(5), true).complete("지시문", "<자료>{}</자료>");

        assertThat(content).isEqualTo("{\"text\": \"구성동에 3명이 원해요.\"}");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        JsonNode sent = objectMapper.readTree(requestBody.get());
        assertThat(sent.path("model").asString()).isEqualTo("test-model");
        assertThat(sent.path("messages").path(0).path("role").asString()).isEqualTo("system");
        assertThat(sent.path("messages").path(0).path("content").asString()).isEqualTo("지시문");
        assertThat(sent.path("messages").path(1).path("role").asString()).isEqualTo("user");
        assertThat(sent.path("response_format").path("type").asString()).isEqualTo("json_object");
        assertThat(sent.path("reasoning_effort").asString()).isEqualTo("none");
    }

    @Test
    @DisplayName("jsonMode=false 면 response_format 을, reasoning_effort 가 비었으면 그 칸을 보내지 않아요")
    void withoutOptionalFields() {
        responseBody = answer("{}");
        client(Duration.ofSeconds(5), false, "").complete("a", "b");
        JsonNode sent = objectMapper.readTree(requestBody.get());
        assertThat(sent.has("response_format")).isFalse();
        assertThat(sent.has("reasoning_effort")).isFalse();
    }

    @Test
    @DisplayName("오류 응답·빈 답·시간 초과는 AI 실패로 바꿔요")
    void failures() {
        status = 500;
        responseBody = "{\"error\": {\"message\": \"boom\"}}";
        assertThatThrownBy(() -> client(Duration.ofSeconds(5), true).complete("a", "b"))
                .isInstanceOf(AiUnavailableException.class).hasMessage("HTTP_500");

        status = 200;
        responseBody = answer("  ");
        assertThatThrownBy(() -> client(Duration.ofSeconds(5), true).complete("a", "b"))
                .isInstanceOf(AiUnavailableException.class).hasMessage("EMPTY_RESPONSE");

        responseBody = "<html>점검 중</html>";
        assertThatThrownBy(() -> client(Duration.ofSeconds(5), true).complete("a", "b"))
                .isInstanceOf(AiUnavailableException.class).hasMessage("BAD_RESPONSE");

        responseBody = answer("{}");
        delayMs = 1500;
        assertThatThrownBy(() -> client(Duration.ofMillis(300), true).complete("a", "b"))
                .isInstanceOf(AiUnavailableException.class).hasMessage("TIMEOUT_OR_CONNECTION");
    }
}
