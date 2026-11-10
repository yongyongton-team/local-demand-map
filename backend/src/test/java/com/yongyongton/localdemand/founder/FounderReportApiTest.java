package com.yongyongton.localdemand.founder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yongyongton.localdemand.ai.AiUnavailableException;
import com.yongyongton.localdemand.ai.AiWriter;
import com.yongyongton.localdemand.ai.SummaryKind;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
 * 창업자 리포트(화면 7)가 공공데이터 기대치 모델(V8)을 근거로 쓰는지 실제 MySQL로 확인해요. Docker가 꺼져 있으면 건너뛰어요.
 * 시드(V3 동·V4 업종·V8 모델 결과)를 그대로 써요. AI는 가짜(Mockito)라 실제 API를 부르지 않아요.
 * 저장된 AI 문장(ai_summary)이 테스트끼리 섞이지 않게 테스트마다 다른 동네·업종을 써요.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class FounderReportApiTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    AiWriter aiWriter;

    @Test
    @DisplayName("V8: 39개 동 × 38개 업종이 다 들어가고, 동마다 인구가 있고, 부족(SHORT) 11쌍은 빈 업종(gap)과 같아요")
    void seedLoaded() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dong_category_estimate", Long.class)).isEqualTo(1482L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dong WHERE population IS NULL", Long.class)).isZero();
        assertThat(count("SHORT")).isEqualTo(11);
        assertThat(count("SOME_NEARBY")).isEqualTo(11);
        assertThat(count("NEXT_DONG")).isEqualTo(52);
        assertThat(count("NOT_JUDGED")).isEqualTo(429);
        assertThat(count("NOT_SHORT")).isEqualTo(979);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM dong_category_estimate e
                JOIN gap g ON g.dong_id = e.dong_id AND g.category_id = e.category_id AND g.status = 'MISSING'
                WHERE e.verdict = 'SHORT'""", Long.class)).isEqualTo(11L);
    }

    @Test
    @DisplayName("주민 요청이 0건이어도 공공데이터 근거로 AI가 설명해요. AI에는 모델 숫자를 넣고, 0인 선주문 숫자는 빼요")
    void aiExplainsWithPublicData() throws Exception {
        long dongId = dongId("구성동");
        long fruit = categoryId("FRUIT_VEG");
        given(aiWriter.writeSummary(eq(SummaryKind.FOUNDER_REPORT), any()))
                .willReturn("구성동은 인구 38122명이라 과일·채소 가게가 보통 9곳 있을 만한데 동 안에는 1곳뿐이에요.");

        mockMvc.perform(get("/api/v1/founder/report").param("dongId", String.valueOf(dongId))
                        .param("categoryId", String.valueOf(fruit))
                        .header("Authorization", "Bearer " + founder()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.requestCount").value(0))
                .andExpect(jsonPath("$.publicData.population").value(38122))
                .andExpect(jsonPath("$.publicData.expectedCount").value(9.0))
                .andExpect(jsonPath("$.publicData.dongStoreCount").value(1))
                .andExpect(jsonPath("$.publicData.verdict").value("SHORT"))
                .andExpect(jsonPath("$.publicData.verdictLabel").value("걸어서 갈 거리로 봐도 부족해요"))
                .andExpect(jsonPath("$.explanation.by").value("AI"))
                .andExpect(jsonPath("$.explanation.text").value(containsString("보통 9곳")))
                .andExpect(jsonPath("$.sources[*].name").value(hasItem("행정안전부 행정동별 주민등록 인구·세대")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> facts = ArgumentCaptor.forClass(Map.class);
        verify(aiWriter).writeSummary(eq(SummaryKind.FOUNDER_REPORT), facts.capture());
        assertThat(facts.getValue())
                .containsEntry("주민등록 인구", 38122)
                .containsEntry("인구 구성으로 본 보통 가게 수", 9L)
                .containsEntry("동 안 실제 가게 수", 1)
                .containsEntry("공공데이터 판정", "걸어서 갈 거리로 봐도 부족해요")
                .doesNotContainKeys("수령 완료된 제안 수", "실제 수령 수량");
    }

    @Test
    @DisplayName("AI를 못 쓰면 기본 문장(TEMPLATE)에 '인구로 보면 보통 몇 곳인데 동 안에는 몇 곳'을 넣어요")
    void templateWithPublicData() throws Exception {
        long dongId = dongId("양지읍");
        long hairSalon = categoryId("HAIR_SALON");
        given(aiWriter.writeSummary(any(), any())).willThrow(new AiUnavailableException("TEST"));

        mockMvc.perform(get("/api/v1/founder/report").param("dongId", String.valueOf(dongId))
                        .param("categoryId", String.valueOf(hairSalon))
                        .header("Authorization", "Bearer " + founder()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicData.expectedCount").value(56.8))
                .andExpect(jsonPath("$.publicData.dongStoreCount").value(23))
                .andExpect(jsonPath("$.explanation.by").value("TEMPLATE"))
                .andExpect(jsonPath("$.explanation.text").value(containsString(
                        "양지읍은 주민등록 인구 22,191명의 나이·가구 구성으로 보면 미용실이 보통 57곳 있을 만한데, 동 안에는 23곳이에요.")))
                .andExpect(jsonPath("$.explanation.text").value(containsString("걸어서 갈 거리로 봐도 부족해요.")));
    }

    @Test
    @DisplayName("모델을 믿기 어려운 업종(NOT_JUDGED)이고 요청도 없으면 AI를 부르지 않고, 기본 문장에 모델 숫자를 넣지 않아요")
    void notJudgedWithoutRequests() throws Exception {
        long dongId = dongId("구성동");
        long bookstore = categoryId("BOOKSTORE");

        mockMvc.perform(get("/api/v1/founder/report").param("dongId", String.valueOf(dongId))
                        .param("categoryId", String.valueOf(bookstore))
                        .header("Authorization", "Bearer " + founder()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicData.verdict").value("NOT_JUDGED"))
                .andExpect(jsonPath("$.explanation.by").value("TEMPLATE"))
                .andExpect(jsonPath("$.explanation.text").value(containsString("주민 요청은 0건이에요")))
                .andExpect(jsonPath("$.explanation.text").value(not(containsString("보통"))));

        verify(aiWriter, never()).writeSummary(any(), any());
    }

    private long count(String verdict) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dong_category_estimate WHERE verdict = ?", Long.class, verdict);
    }

    private long dongId(String name) {
        return jdbc.queryForObject("SELECT id FROM dong WHERE name = ?", Long.class, name);
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
