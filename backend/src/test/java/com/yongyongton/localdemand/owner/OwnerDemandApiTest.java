package com.yongyongton.localdemand.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yongyongton.localdemand.ai.AiUnavailableException;
import com.yongyongton.localdemand.ai.AiWriter;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/**
 * 사장님 화면 API를 실제 MySQL로 확인해요. Docker가 꺼져 있으면 건너뛰어요.
 * AI는 가짜(Mockito)로 바꿔서 실제 API를 부르지 않아요.
 * 테스트끼리 섞이지 않게 테스트마다 새 업종을 만들어 써요.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OwnerDemandApiTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    /**
     * 내 가게 위치. 실제 용인 동 경계(V3 시드)와 겹치지 않게 한국 밖(위도 10, 경도 100)을 써요.
     * 동 A는 1.1km, 동 B는 2.2km, 먼 동은 4.4km 떨어져 있어요.
     */
    private static final double SHOP_LAT = 10.00;
    private static final double SHOP_LON = 100.00;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    AiWriter aiWriter;

    @Test
    @DisplayName("화면 4: 기본 3km 안, 요청 많은 순. 거리·많이 고른 시간대·같은 업종 가게 수")
    void demandWithinRadius() throws Exception {
        long categoryId = insertCategory("과일·채소 가게");
        long dongA = insertDong("가동", "기흥구", 10.00, 100.01);
        long dongB = insertDong("나동", "수지구", 10.02, 100.00);
        long far = insertDong("먼동", "기흥구", 10.00, 100.04);
        insertRequest(dongA, categoryId, "", "WEEKEND");
        insertRequest(dongB, categoryId, "LATE_OPEN", "WEEKDAY_EVENING,WEEKEND");
        insertRequest(dongB, categoryId, "", "WEEKDAY_EVENING");
        insertRequest(far, categoryId, "", "WEEKDAY_DAY");
        insertRequest(far, categoryId, "", "WEEKDAY_DAY");
        insertRequest(far, categoryId, "", "WEEKDAY_DAY");
        insertStore(dongB, categoryId);
        insertStore(dongB, categoryId);
        insertStore(dongA, insertCategory("빵집"));
        String token = owner(categoryId);

        mockMvc.perform(get("/api/v1/owner/demand").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shop.shopName").value("싱싱과일"))
                .andExpect(jsonPath("$.shop.category.id").value(categoryId))
                .andExpect(jsonPath("$.shop.category.name").value("과일·채소 가게"))
                .andExpect(jsonPath("$.shop.dong").value(nullValue()))
                .andExpect(jsonPath("$.shop.lat").value(SHOP_LAT))
                .andExpect(jsonPath("$.radiusKm").value(3.0))
                .andExpect(jsonPath("$.gu").value(nullValue()))
                .andExpect(jsonPath("$.areas", hasSize(2)))
                .andExpect(jsonPath("$.areas[0].dongId").value(dongB))
                .andExpect(jsonPath("$.areas[0].name").value("나동"))
                .andExpect(jsonPath("$.areas[0].requestCount").value(2))
                .andExpect(jsonPath("$.areas[0].distanceKm").value(2.2))
                .andExpect(jsonPath("$.areas[0].topTimeSlot").value("WEEKDAY_EVENING"))
                .andExpect(jsonPath("$.areas[0].sameCategoryStoreCount").value(2))
                .andExpect(jsonPath("$.areas[0].center.lat").value(10.02))
                .andExpect(jsonPath("$.areas[0].center.lon").value(100.00))
                .andExpect(jsonPath("$.areas[1].dongId").value(dongA))
                .andExpect(jsonPath("$.areas[1].distanceKm").value(1.1))
                .andExpect(jsonPath("$.areas[1].topTimeSlot").value("WEEKEND"))
                .andExpect(jsonPath("$.areas[1].sameCategoryStoreCount").value(0));

        // 5km 안이면 먼 동(요청 3)이 맨 위
        mockMvc.perform(get("/api/v1/owner/demand").param("radiusKm", "5").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.radiusKm").value(5.0))
                .andExpect(jsonPath("$.areas", hasSize(3)))
                .andExpect(jsonPath("$.areas[0].dongId").value(far))
                .andExpect(jsonPath("$.areas[0].distanceKm").value(4.4));

        // 구 전체: 거리와 상관없이 그 구의 동만
        mockMvc.perform(get("/api/v1/owner/demand").param("gu", "기흥구").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.radiusKm").value(nullValue()))
                .andExpect(jsonPath("$.gu").value("기흥구"))
                .andExpect(jsonPath("$.areas", hasSize(2)))
                .andExpect(jsonPath("$.areas[0].dongId").value(far))
                .andExpect(jsonPath("$.areas[1].dongId").value(dongA));

        mockMvc.perform(get("/api/v1/owner/demand").param("radiusKm", "0").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/owner/demand").param("radiusKm", "abc").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("화면 5: 조건·시간대를 많은 순으로 세고, AI를 못 쓰면 기본 문장(TEMPLATE)")
    void detailWithTemplate() throws Exception {
        given(aiWriter.writeSummary(any(), any())).willThrow(new AiUnavailableException("TEST"));
        long categoryId = insertCategory("과일·채소 가게");
        long dongId = insertDong("보정동", "기흥구", 37.32, 127.10);
        insertRequest(dongId, categoryId, "LATE_OPEN,SMALL_QTY", "WEEKDAY_EVENING,WEEKEND");
        insertRequest(dongId, categoryId, "LATE_OPEN", "WEEKDAY_EVENING");
        insertStore(dongId, categoryId);
        String token = owner(categoryId);

        mockMvc.perform(get("/api/v1/owner/demand/" + dongId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dong.id").value(dongId))
                .andExpect(jsonPath("$.dong.name").value("보정동"))
                .andExpect(jsonPath("$.category.id").value(categoryId))
                .andExpect(jsonPath("$.requestCount").value(2))
                .andExpect(jsonPath("$.sameCategoryStoreCount").value(1))
                .andExpect(jsonPath("$.conditions", hasSize(2)))
                .andExpect(jsonPath("$.conditions[0].code").value("LATE_OPEN"))
                .andExpect(jsonPath("$.conditions[0].label").value("늦게까지 영업"))
                .andExpect(jsonPath("$.conditions[0].count").value(2))
                .andExpect(jsonPath("$.conditions[1].code").value("SMALL_QTY"))
                .andExpect(jsonPath("$.timeSlots", hasSize(2)))
                .andExpect(jsonPath("$.timeSlots[0].slot").value("WEEKDAY_EVENING"))
                .andExpect(jsonPath("$.timeSlots[0].count").value(2))
                .andExpect(jsonPath("$.timeSlots[0].ratio").value(1.0))
                .andExpect(jsonPath("$.timeSlots[1].slot").value("WEEKEND"))
                .andExpect(jsonPath("$.timeSlots[1].ratio").value(0.5))
                .andExpect(jsonPath("$.summary.by").value("TEMPLATE"))
                .andExpect(jsonPath("$.summary.text").value("보정동에는 과일·채소 가게를 원하는 주민이 2명 있어요."
                        + " 평일 저녁에 갈 수 있다는 요청이 가장 많아요(2명). 가장 많이 바라는 점은 '늦게까지 영업'이에요(2명)."));
    }

    @Test
    @DisplayName("화면 5: 검사를 통과한 AI 문장은 저장해 두고, 같은 숫자면 AI를 다시 부르지 않아요")
    void aiSummaryIsCached() throws Exception {
        given(aiWriter.writeSummary(any(), any())).willReturn("영덕1동에는 반찬가게를 원하는 주민이 1명 있어요.");
        long categoryId = insertCategory("반찬가게");
        long dongId = insertDong("영덕1동", "기흥구", 37.27, 127.10);
        insertRequest(dongId, categoryId, "", "WEEKEND");
        String token = owner(categoryId);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/owner/demand/" + dongId).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.by").value("AI"))
                    .andExpect(jsonPath("$.summary.text").value("영덕1동에는 반찬가게를 원하는 주민이 1명 있어요."));
        }
        verify(aiWriter, times(1)).writeSummary(any(), any());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_summary WHERE dong_id = ? AND category_id = ?",
                Integer.class, dongId, categoryId)).isEqualTo(1);

        // 요청이 늘어 숫자가 바뀌면 다시 불러요
        insertRequest(dongId, categoryId, "", "WEEKEND");
        mockMvc.perform(get("/api/v1/owner/demand/" + dongId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.by").value("TEMPLATE"));
        verify(aiWriter, times(2)).writeSummary(any(), any());
    }

    @Test
    @DisplayName("화면 5: 자료에 없는 숫자나 단정 표현이 있으면 버리고 기본 문장, 저장도 안 해요")
    void aiSummaryRejected() throws Exception {
        given(aiWriter.writeSummary(any(), any())).willReturn("주민 15명이 원해요. 반드시 잘될 거예요.");
        long categoryId = insertCategory("꽃집");
        long dongId = insertDong("마북동", "기흥구", 37.30, 127.13);
        insertRequest(dongId, categoryId, "", "WEEKEND");
        String token = owner(categoryId);

        mockMvc.perform(get("/api/v1/owner/demand/" + dongId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.by").value("TEMPLATE"))
                .andExpect(jsonPath("$.summary.text").value("마북동에는 꽃집을 원하는 주민이 1명 있어요."
                        + " 주말에 갈 수 있다는 요청이 가장 많아요(1명)."));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_summary WHERE category_id = ?",
                Integer.class, categoryId)).isZero();
    }

    @Test
    @DisplayName("권한·없는 것: 주민 403, 가게 없는 사장님 400(업종을 주면 화면 5는 돼요), 없는 동·업종 404, 요청 0이면 AI 없이 기본 문장")
    void rulesAndErrors() throws Exception {
        long categoryId = insertCategory("세탁소");
        long dongId = insertDong("신갈동", "기흥구", 37.27, 127.11);

        String resident = guest();
        mockMvc.perform(put("/api/v1/me").header("Authorization", "Bearer " + resident)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"RESIDENT\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/owner/demand").header("Authorization", "Bearer " + resident))
                .andExpect(status().isForbidden());

        String noShop = guest();
        mockMvc.perform(put("/api/v1/me").header("Authorization", "Bearer " + noShop)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"OWNER\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/owner/demand").header("Authorization", "Bearer " + noShop))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("먼저 가게를 등록해 주세요."));
        mockMvc.perform(get("/api/v1/owner/demand/" + dongId).header("Authorization", "Bearer " + noShop))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/owner/demand/" + dongId).param("categoryId", String.valueOf(categoryId))
                        .header("Authorization", "Bearer " + noShop))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestCount").value(0))
                .andExpect(jsonPath("$.conditions", hasSize(0)))
                .andExpect(jsonPath("$.summary.by").value("TEMPLATE"))
                .andExpect(jsonPath("$.summary.text").value("신갈동에는 아직 세탁소를 원하는 요청이 없어요."));
        verify(aiWriter, times(0)).writeSummary(any(), any());

        mockMvc.perform(get("/api/v1/owner/demand/999999").param("categoryId", String.valueOf(categoryId))
                        .header("Authorization", "Bearer " + noShop))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/owner/demand/" + dongId).param("categoryId", "999999")
                        .header("Authorization", "Bearer " + noShop))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/owner/demand"))
                .andExpect(status().isUnauthorized());
    }

    /** 손님 계정 → 사장님 → 가게 등록(SHOP_LAT, SHOP_LON). 테스트 동에는 경계가 없어서 가게 동은 null. */
    private String owner(long categoryId) throws Exception {
        String token = guest();
        mockMvc.perform(put("/api/v1/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"OWNER\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/me/shop").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shopName\": \"싱싱과일\", \"categoryId\": " + categoryId
                                + ", \"lat\": " + SHOP_LAT + ", \"lon\": " + SHOP_LON + "}"))
                .andExpect(status().isOk());
        return token;
    }

    private String guest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/guest")).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("accessToken").asString();
    }

    private long insertDong(String name, String gu, double lat, double lon) {
        String admCode = UUID.randomUUID().toString().substring(0, 10);
        jdbc.update("INSERT INTO dong (adm_code, name, gu, center_lat, center_lon) VALUES (?, ?, ?, ?, ?)",
                admCode, name, gu, lat, lon);
        return jdbc.queryForObject("SELECT id FROM dong WHERE adm_code = ?", Long.class, admCode);
    }

    private long insertCategory(String name) {
        String code = "C" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO category (code, name, sbiz_name, mart_check) VALUES (?, ?, '테스트 업종', FALSE)",
                code, name);
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
    }

    /** 주민 한 명을 만들고 요청 하나를 바로 DB에 넣어요. */
    private void insertRequest(long dongId, long categoryId, String conditions, String timeSlots) {
        String nickname = "주민" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO app_user (nickname, role, dong_id) VALUES (?, 'RESIDENT', ?)", nickname, dongId);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE nickname = ?", Long.class, nickname);
        jdbc.update("INSERT INTO shop_request (user_id, dong_id, category_id, conditions, time_slots, frequency)"
                + " VALUES (?, ?, ?, ?, ?, 'WEEKLY_PLUS')", userId, dongId, categoryId, conditions, timeSlots);
    }

    private void insertStore(long dongId, long categoryId) {
        jdbc.update("INSERT INTO store (source_id, name, category_id, lat, lon, dong_id) VALUES (?, '기존가게', ?, 0, 0, ?)",
                UUID.randomUUID().toString().substring(0, 20), categoryId, dongId);
    }
}
