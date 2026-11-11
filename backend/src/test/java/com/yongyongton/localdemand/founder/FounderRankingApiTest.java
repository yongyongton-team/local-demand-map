package com.yongyongton.localdemand.founder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 창업자 입지 순위(화면 6)가 요청이 없는 업종도 공공데이터 부족 판정(V8)으로 줄을 세우는지 실제 MySQL로 확인해요.
 * Docker가 꺼져 있으면 건너뛰어요. 시드(V3 동·V4 업종·V8 모델 결과)를 그대로 쓰고,
 * 주민 요청은 테스트마다 다른 업종에만 넣어서 서로 섞이지 않아요.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class FounderRankingApiTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("요청이 0건인 업종(미용실)도 39개 동이 다 나오고, 공공데이터로 부족(SHORT)한 동네가 맨 위예요")
    void noRequestsRankedByPublicData() throws Exception {
        List<JsonNode> items = rankings("HAIR_SALON");

        assertThat(items).hasSize(39);
        assertThat(items).allSatisfy(item -> assertThat(item.get("requestCount").asLong()).isZero());

        // V8: 미용실 SHORT 5곳, SOME_NEARBY 2곳
        assertThat(items.subList(0, 5)).extracting(this::verdict).containsOnly("SHORT");
        assertThat(items.subList(0, 5)).extracting(this::name)
                .containsExactlyInAnyOrder("모현읍", "남사읍", "양지읍", "원삼면", "기흥동");
        assertThat(items.subList(5, 7)).extracting(this::verdict).containsOnly("SOME_NEARBY");
        assertThat(items.subList(5, 7)).extracting(this::name).containsExactlyInAnyOrder("삼가동", "구성동");

        // 순위에도 리포트와 같은 근거 숫자가 붙어요
        JsonNode yangji = items.stream().filter(item -> name(item).equals("양지읍")).findFirst().orElseThrow();
        assertThat(yangji.get("publicData").get("expectedCount").asDouble()).isEqualTo(56.8);
        assertThat(yangji.get("publicData").get("dongStoreCount").asInt()).isEqualTo(23);
        assertThat(yangji.get("publicData").get("population").asInt()).isEqualTo(22191);
        assertThat(yangji.get("publicData").get("verdictLabel").asString()).isEqualTo("걸어서 갈 거리로 봐도 부족해요");

        // 그다음 "동 안은 부족하지만 옆 동네에는 있어요"(NEXT_DONG) 9곳, 나머지는 뚜렷하게 부족하지 않은 곳
        assertThat(items.subList(7, 16)).extracting(this::verdict).containsOnly("NEXT_DONG");
        assertThat(items.subList(16, 39)).extracting(this::verdict).containsOnly("NOT_SHORT");

        // 판정 단계 안에서는 부족이 뚜렷한 순(기대치가 맞다면 이만큼 적을 확률이 작은 순)이에요
        for (List<JsonNode> tier : List.of(items.subList(0, 5), items.subList(5, 7), items.subList(7, 16), items.subList(16, 39))) {
            assertThat(tier.stream().map(item -> probFewer("HAIR_SALON", name(item))).toList()).isSorted();
        }
    }

    @Test
    @DisplayName("데이터 부족이 먼저이고, 같은 판정 단계 안에서는 주민 요청이 많은 동네가 앞서요")
    void publicDataFirstThenRequests() throws Exception {
        // 과일·채소: 구성동 SHORT, 성복동 SOME_NEARBY, 마북동·보정동 NOT_SHORT (NEXT_DONG 없음)
        addRequests("구성동", "FRUIT_VEG", 1);
        addRequests("마북동", "FRUIT_VEG", 5);
        addRequests("보정동", "FRUIT_VEG", 3);

        List<JsonNode> items = rankings("FRUIT_VEG");

        assertThat(items).extracting(this::name).startsWith("구성동", "성복동", "마북동", "보정동");
        assertThat(items.get(1).get("requestCount").asLong()).isZero();
        assertThat(items.get(2).get("requestCount").asLong()).isEqualTo(5);
        assertThat(items.get(3).get("requestCount").asLong()).isEqualTo(3);
        assertThat(items).extracting(item -> item.get("rank").asInt()).startsWith(1, 2, 3, 4);
    }

    @Test
    @DisplayName("모델을 믿기 어려운 업종(NOT_JUDGED)은 부족 판정 없이 요청 수와 예전 점수로 줄을 세워요")
    void notJudgedFallsBackToRequests() throws Exception {
        addRequests("포곡읍", "BOOKSTORE", 2);

        List<JsonNode> items = rankings("BOOKSTORE");

        assertThat(items).hasSize(39);
        assertThat(items).extracting(this::verdict).containsOnly("NOT_JUDGED");
        assertThat(name(items.get(0))).isEqualTo("포곡읍");
        List<Double> scores = items.subList(1, 39).stream().map(item -> item.get("score").asDouble()).toList();
        assertThat(scores).isSortedAccordingTo((a, b) -> Double.compare(b, a));
    }

    @Test
    @DisplayName("limit 만큼만 돌려줘요")
    void limit() throws Exception {
        assertThat(rankings("HAIR_SALON", 3)).hasSize(3);
    }

    private List<JsonNode> rankings(String categoryCode) throws Exception {
        return rankings(categoryCode, 39);
    }

    private List<JsonNode> rankings(String categoryCode, int limit) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/founder/rankings")
                        .param("categoryId", String.valueOf(categoryId(categoryCode)))
                        .param("limit", String.valueOf(limit))
                        .header("Authorization", "Bearer " + founder()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<JsonNode> items = new ArrayList<>();
        body.get("items").forEach(items::add);
        return items;
    }

    private String name(JsonNode item) {
        return item.get("name").asString();
    }

    private String verdict(JsonNode item) {
        return item.get("publicData").get("verdict").asString();
    }

    private double probFewer(String categoryCode, String dongName) {
        return jdbc.queryForObject("""
                SELECT e.prob_fewer FROM dong_category_estimate e
                JOIN dong d ON d.id = e.dong_id JOIN category c ON c.id = e.category_id
                WHERE d.name = ? AND c.code = ?""", Double.class, dongName, categoryCode);
    }

    /** 주민 n명이 그 동네에 그 업종을 요청한 것으로 넣어요 */
    private void addRequests(String dongName, String categoryCode, int n) {
        long dongId = jdbc.queryForObject("SELECT id FROM dong WHERE name = ?", Long.class, dongName);
        long categoryId = categoryId(categoryCode);
        for (int i = 0; i < n; i++) {
            jdbc.update("INSERT INTO app_user (nickname, role, dong_id) VALUES ('순위시험주민', 'RESIDENT', ?)", dongId);
            long userId = jdbc.queryForObject("SELECT MAX(id) FROM app_user", Long.class);
            jdbc.update("INSERT INTO shop_request (user_id, dong_id, category_id, time_slots) VALUES (?, ?, ?, 'WEEKEND')",
                    userId, dongId, categoryId);
        }
    }

    private long categoryId(String code) {
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
    }

    private String founder() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/guest")).andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("accessToken").asString();
        mockMvc.perform(put("/api/v1/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"FOUNDER\"}"))
                .andExpect(status().isOk());
        return token;
    }
}
