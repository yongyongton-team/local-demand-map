package com.yongyongton.localdemand.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/**
 * 주민 요청 API를 실제 MySQL로 확인해요. Docker가 꺼져 있으면 건너뛰어요.
 * "모였어요" 기준(goal)은 이 테스트에서만 2로 낮춰요.
 */
@SpringBootTest(properties = {"app.demo-login.enabled=true", "app.request.goal=2"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RequestApiTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    private static final double CENTER_LAT = 37.30;
    private static final double CENTER_LON = 127.11;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("요청 보내기: 201, 명세 모양, 시각은 +09:00. 같은 동·업종을 또 보내면 409")
    void createAndDuplicate() throws Exception {
        long dongId = insertDong("구성동");
        long categoryId = insertCategory("과일·채소 가게");
        String token = resident(dongId);

        mockMvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(dongId, categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.request.id").isNumber())
                .andExpect(jsonPath("$.request.dongId").value(dongId))
                .andExpect(jsonPath("$.request.categoryId").value(categoryId))
                .andExpect(jsonPath("$.request.createdAt",
                        matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+09:00")))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.goal").value(2))
                .andExpect(jsonPath("$.reachedGoal").value(false))
                .andExpect(jsonPath("$.notifiedOwnerCount").value(0));

        mockMvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(dongId, categoryId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REQUESTED"));
    }

    @Test
    @DisplayName("기준 수가 차면 reachedGoal=true, 동 중심 3km 안 같은 업종 사장님 수를 알려 줘요")
    void reachedGoal() throws Exception {
        long dongId = insertDong("보정동");
        long categoryId = insertCategory("반찬가게");
        insertShop(categoryId, CENTER_LAT + 0.01, CENTER_LON);  // 약 1.1km → 셈
        insertShop(categoryId, CENTER_LAT + 0.10, CENTER_LON);  // 약 11km → 안 셈

        mockMvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + resident(dongId))
                        .contentType(MediaType.APPLICATION_JSON).content(body(dongId, categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reachedGoal").value(false));
        mockMvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + resident(dongId))
                        .contentType(MediaType.APPLICATION_JSON).content(body(dongId, categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.reachedGoal").value(true))
                .andExpect(jsonPath("$.notifiedOwnerCount").value(1));
    }

    @Test
    @DisplayName("내 동네가 아니면 400, 주민이 아니면 403, 없는 업종이면 404")
    void rules() throws Exception {
        long myDong = insertDong("마북동");
        long otherDong = insertDong("신갈동");
        long categoryId = insertCategory("빵집");

        mockMvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + resident(myDong))
                        .contentType(MediaType.APPLICATION_JSON).content(body(otherDong, categoryId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        String guest = token(mockMvc.perform(post("/api/v1/auth/guest")).andReturn());
        mockMvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + guest)
                        .contentType(MediaType.APPLICATION_JSON).content(body(myDong, categoryId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + resident(myDong))
                        .contentType(MediaType.APPLICATION_JSON).content(body(myDong, 999999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("시간대가 없거나 목록에 없는 조건 코드면 400")
    void validation() throws Exception {
        long dongId = insertDong("영덕1동");
        long categoryId = insertCategory("꽃집");
        String token = resident(dongId);

        mockMvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dongId\": " + dongId + ", \"categoryId\": " + categoryId
                                + ", \"timeSlots\": [], \"frequency\": \"WEEKLY_PLUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dongId\": " + dongId + ", \"categoryId\": " + categoryId
                                + ", \"conditions\": [\"FOO\"], \"timeSlots\": [\"WEEKEND\"], \"frequency\": \"WEEKLY_PLUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("내 요청 목록과 취소: 처음엔 [], 보내면 명세 모양(offers=[]), 취소하면 204 후 다시 []")
    void listAndDelete() throws Exception {
        long dongId = insertDong("기흥동");
        long categoryId = insertCategory("요가·필라테스");
        String token = resident(dongId);

        mockMvc.perform(get("/api/v1/me/requests").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        MvcResult created = mockMvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(dongId, categoryId)))
                .andExpect(status().isCreated()).andReturn();
        long requestId = json(created).get("request").get("id").asLong();

        mockMvc.perform(get("/api/v1/me/requests").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(requestId))
                .andExpect(jsonPath("$[0].dong.name").value("기흥동"))
                .andExpect(jsonPath("$[0].category.name").value("요가·필라테스"))
                .andExpect(jsonPath("$[0].count").value(1))
                .andExpect(jsonPath("$[0].timeSlots[0]").value("WEEKDAY_EVENING"))
                .andExpect(jsonPath("$[0].timeSlots[1]").value("WEEKEND"))
                .andExpect(jsonPath("$[0].frequency").value("WEEKLY_PLUS"))
                .andExpect(jsonPath("$[0].createdAt", matchesPattern(".*\\+09:00")))
                .andExpect(jsonPath("$[0].offers", hasSize(0)));

        // 남의 요청은 못 지워요(403), 없는 요청은 404
        mockMvc.perform(delete("/api/v1/requests/" + requestId)
                        .header("Authorization", "Bearer " + resident(dongId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/requests/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/requests/" + requestId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/me/requests").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("하버사인 거리: 위도 0.01도 ≈ 1.11km")
    void distance() {
        assertThat(RequestService.distanceKm(37.30, 127.11, 37.31, 127.11)).isBetween(1.10, 1.12);
    }

    /** 손님 계정을 만들고 주민 + 동네로 정한 뒤 토큰을 돌려줘요. */
    private String resident(long dongId) throws Exception {
        String token = token(mockMvc.perform(post("/api/v1/auth/guest")).andReturn());
        mockMvc.perform(put("/api/v1/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"RESIDENT\", \"dongId\": " + dongId + "}"))
                .andExpect(status().isOk());
        return token;
    }

    private static String body(long dongId, long categoryId) {
        return "{\"dongId\": " + dongId + ", \"categoryId\": " + categoryId
                + ", \"text\": \"밤 10시까지 하는 가게요\", \"conditions\": [\"LATE_OPEN\", \"SMALL_QTY\"]"
                + ", \"timeSlots\": [\"WEEKDAY_EVENING\", \"WEEKEND\"], \"frequency\": \"WEEKLY_PLUS\"}";
    }

    private tools.jackson.databind.JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String token(MvcResult result) throws Exception {
        return json(result).get("accessToken").asString();
    }

    private long insertDong(String name) {
        String admCode = UUID.randomUUID().toString().substring(0, 10);
        jdbc.update("INSERT INTO dong (adm_code, name, gu, center_lat, center_lon) VALUES (?, ?, '기흥구', ?, ?)",
                admCode, name, CENTER_LAT, CENTER_LON);
        return jdbc.queryForObject("SELECT id FROM dong WHERE adm_code = ?", Long.class, admCode);
    }

    private long insertCategory(String name) {
        String code = "C" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO category (code, name, sbiz_name, mart_check) VALUES (?, ?, '테스트 업종', TRUE)", code, name);
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
    }

    /** 사장님 계정과 가게를 바로 DB에 넣어요. */
    private void insertShop(long categoryId, double lat, double lon) {
        String nickname = "사장" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO app_user (nickname, role) VALUES (?, 'OWNER')", nickname);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE nickname = ?", Long.class, nickname);
        jdbc.update("INSERT INTO owner_shop (user_id, shop_name, category_id, lat, lon) VALUES (?, '테스트가게', ?, ?, ?)",
                userId, categoryId, lat, lon);
    }
}
