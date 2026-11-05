package com.yongyongton.localdemand.ai;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yongyongton.localdemand.ai.AiWriter.ParsedRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/**
 * 요청 문장 정리 API를 실제 MySQL로 확인해요. Docker가 꺼져 있으면 건너뛰어요.
 * AI는 가짜(Mockito)로 바꿔서, AI가 이상한 코드를 내도 서버가 걸러내는지 봐요.
 * 테스트끼리 섞이지 않게 테스트마다 새 동·업종을 만들어 써요.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RequestParseApiTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    private static final String TEXT = "밤 10시까지 하는 과일가게요. 낱개로 살 수 있으면 좋겠어요.";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    AiWriter aiWriter;

    @Test
    @DisplayName("AI가 고른 업종·조건 중 목록에 있는 것만 남기고, 보낸 요청이 있으면 alreadyRequested")
    void parseAndAlreadyRequested() throws Exception {
        long dongId = insertDong("구성동");
        String fruit = insertCategory("과일·채소 가게");
        long fruitId = categoryId(fruit);
        given(aiWriter.by()).willReturn("AI");
        given(aiWriter.parseRequest(eq(TEXT), any(), any())).willReturn(new ParsedRequest(
                fruit, List.of("LATE_OPEN", "FOO", "SMALL_QTY", "LATE_OPEN"), List.of("NOPE")));
        String token = resident(dongId);

        mockMvc.perform(parse(token, dongId, "  " + TEXT + "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.id").value(fruitId))
                .andExpect(jsonPath("$.category.name").value("과일·채소 가게"))
                .andExpect(jsonPath("$.conditions", hasSize(2)))
                .andExpect(jsonPath("$.conditions[0].code").value("LATE_OPEN"))
                .andExpect(jsonPath("$.conditions[0].label").value("늦게까지 영업"))
                .andExpect(jsonPath("$.conditions[1].code").value("SMALL_QTY"))
                .andExpect(jsonPath("$.alreadyRequested").value(false))
                .andExpect(jsonPath("$.candidates", hasSize(0)))
                .andExpect(jsonPath("$.by").value("AI"));

        mockMvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dongId\": " + dongId + ", \"categoryId\": " + fruitId
                                + ", \"timeSlots\": [\"WEEKEND\"], \"frequency\": \"WEEKLY_PLUS\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(parse(token, dongId, TEXT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRequested").value(true));
    }

    @Test
    @DisplayName("업종을 못 정하면 category null, 후보는 AI 후보(목록에 있는 것) → 이 동네에서 요청 많은 업종 순으로 3개까지")
    void candidates() throws Exception {
        long dongId = insertDong("마북동");
        String bakery = insertCategory("빵집");
        String sideDish = insertCategory("반찬가게");
        String flower = insertCategory("꽃집");
        // 이 동네 요청: 꽃집 2, 반찬가게 1
        insertRequest(dongId, categoryId(flower));
        insertRequest(dongId, categoryId(flower));
        insertRequest(dongId, categoryId(sideDish));
        given(aiWriter.parseRequest(any(), any(), any())).willReturn(new ParsedRequest(
                "", List.of(), List.of(bakery, "NOPE", sideDish)));
        String token = resident(dongId);

        mockMvc.perform(parse(token, dongId, "뭔가 맛있는 거"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value(nullValue()))
                .andExpect(jsonPath("$.alreadyRequested").value(false))
                .andExpect(jsonPath("$.candidates", hasSize(3)))
                .andExpect(jsonPath("$.candidates[0].name").value("빵집"))
                .andExpect(jsonPath("$.candidates[1].name").value("반찬가게"))
                .andExpect(jsonPath("$.candidates[2].name").value("꽃집"));
    }

    @Test
    @DisplayName("AI를 쓸 수 없으면 502 AI_UNAVAILABLE")
    void aiUnavailable() throws Exception {
        long dongId = insertDong("보정동");
        given(aiWriter.parseRequest(any(), any(), any())).willThrow(new AiUnavailableException("TIMEOUT_OR_CONNECTION"));
        String token = resident(dongId);

        mockMvc.perform(parse(token, dongId, TEXT))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
    }

    @Test
    @DisplayName("주민만(403), 문장은 1~200자(400), 없는 동은 404. 이때는 AI를 부르지 않아요")
    void rules() throws Exception {
        long dongId = insertDong("상갈동");
        String token = resident(dongId);

        mockMvc.perform(parse(token, dongId, "가".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(parse(token, dongId, "   "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(parse(token, 999999, TEXT))
                .andExpect(status().isNotFound());

        String owner = guest();
        mockMvc.perform(put("/api/v1/me").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"OWNER\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(parse(owner, dongId, TEXT))
                .andExpect(status().isForbidden());
        verify(aiWriter, never()).parseRequest(any(), any(), any());
    }

    private RequestBuilder parse(String token, long dongId, String text) {
        return post("/api/v1/requests/parse").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("dongId", dongId, "text", text)));
    }

    private String resident(long dongId) throws Exception {
        String token = guest();
        mockMvc.perform(put("/api/v1/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"RESIDENT\", \"dongId\": " + dongId + "}"))
                .andExpect(status().isOk());
        return token;
    }

    private String guest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/guest")).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("accessToken").asString();
    }

    private long insertDong(String name) {
        String admCode = UUID.randomUUID().toString().substring(0, 10);
        jdbc.update("INSERT INTO dong (adm_code, name, gu, center_lat, center_lon) VALUES (?, ?, '기흥구', 37.30, 127.11)",
                admCode, name);
        return jdbc.queryForObject("SELECT id FROM dong WHERE adm_code = ?", Long.class, admCode);
    }

    /** 새 업종을 넣고 코드를 돌려줘요. */
    private String insertCategory(String name) {
        String code = "C" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO category (code, name, sbiz_name, mart_check) VALUES (?, ?, '테스트 업종', FALSE)",
                code, name);
        return code;
    }

    private long categoryId(String code) {
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
    }

    private void insertRequest(long dongId, long categoryId) {
        String nickname = "주민" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO app_user (nickname, role, dong_id) VALUES (?, 'RESIDENT', ?)", nickname, dongId);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE nickname = ?", Long.class, nickname);
        jdbc.update("INSERT INTO shop_request (user_id, dong_id, category_id, time_slots, frequency)"
                + " VALUES (?, ?, ?, 'WEEKEND', 'WEEKLY_PLUS')", userId, dongId, categoryId);
    }
}
