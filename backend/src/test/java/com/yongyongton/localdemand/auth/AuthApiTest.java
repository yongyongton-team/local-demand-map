package com.yongyongton.localdemand.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** POST /auth/guest, POST /auth/demo 를 실제 MySQL로 확인해요. Docker가 꺼져 있으면 건너뛰어요. */
@SpringBootTest(properties = "app.demo-login.enabled=true")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthApiTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("손님 계정: 새 계정과 30일짜리 입장권을 주고, 입장권 안에는 계정 id가 들어 있어요")
    void guest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(2592000))
                .andExpect(jsonPath("$.isNew").value(true))
                .andExpect(jsonPath("$.user.nickname", startsWith("손님")))
                .andExpect(jsonPath("$.user.role").value(nullValue()))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        long userId = body.get("user").get("id").asLong();
        assertThat(body.get("user").get("nickname").asString()).isEqualTo("손님" + userId);

        Jwt jwt = jwtDecoder.decode(body.get("accessToken").asString());
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(userId));
    }

    @Test
    @DisplayName("시연 로그인: 같은 역할로 두 번 부르면 같은 계정이에요")
    void demoReusesAccount() throws Exception {
        String request = "{\"role\": \"OWNER\"}";
        long first = userId(mockMvc.perform(post("/api/v1/auth/demo")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("OWNER"))
                .andReturn());
        long second = userId(mockMvc.perform(post("/api/v1/auth/demo")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNew").value(false))
                .andReturn());
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("시연 로그인: 역할이 없으면 400 VALIDATION_ERROR")
    void demoNeedsRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/demo").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("토큰 확인: 진짜 토큰은 통과(없는 주소라 404), 가짜 토큰·토큰 없음은 401")
    void tokenFilter() throws Exception {
        String token = token(mockMvc.perform(post("/api/v1/auth/guest")).andReturn());

        mockMvc.perform(get("/api/v1/nothing-here").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/nothing-here").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/nothing-here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("토큰 확인: 망가진 토큰이 붙어 와도 /auth/guest 는 새 토큰을 줘요")
    void guestIgnoresOldToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/guest").header("Authorization", "Bearer expired-or-broken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    @DisplayName("내 정보: 막 만든 손님은 역할·동네·가게가 모두 null")
    void meForNewGuest() throws Exception {
        String token = token(mockMvc.perform(post("/api/v1/auth/guest")).andReturn());
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname", startsWith("손님")))
                .andExpect(jsonPath("$.role").value(nullValue()))
                .andExpect(jsonPath("$.dong").value(nullValue()))
                .andExpect(jsonPath("$.shop").value(nullValue()));
    }

    @Test
    @DisplayName("PUT /me: 역할·동네를 정하면 GET /me 와 같은 모양으로 돌려줘요")
    void updateMe() throws Exception {
        long dongId = insertDong("구성동", "기흥구");
        String token = token(mockMvc.perform(post("/api/v1/auth/guest")).andReturn());

        mockMvc.perform(put("/api/v1/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"RESIDENT\", \"dongId\": " + dongId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RESIDENT"))
                .andExpect(jsonPath("$.dong.id").value(dongId))
                .andExpect(jsonPath("$.dong.name").value("구성동"))
                .andExpect(jsonPath("$.dong.gu").value("기흥구"))
                .andExpect(jsonPath("$.shop").value(nullValue()));

        // 역할만 바꾸면 동네는 그대로
        mockMvc.perform(put("/api/v1/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"FOUNDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FOUNDER"))
                .andExpect(jsonPath("$.dong.id").value(dongId));
    }

    @Test
    @DisplayName("PUT /me: 없는 동이면 404, 역할이 없으면 400")
    void updateMeErrors() throws Exception {
        String token = token(mockMvc.perform(post("/api/v1/auth/guest")).andReturn());
        mockMvc.perform(put("/api/v1/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"RESIDENT\", \"dongId\": 999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(put("/api/v1/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dongId\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("내 정보: 사장님은 가게가 명세 모양으로 나와요")
    void meWithShop() throws Exception {
        long dongId = insertDong("신갈동", "기흥구");
        String code = "C" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO category (code, name, sbiz_name, mart_check) VALUES (?, '과일·채소 가게', '채소/과일 소매업', TRUE)", code);
        long categoryId = jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/demo")
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"OWNER\"}")).andReturn();
        jdbc.update("INSERT INTO owner_shop (user_id, shop_name, category_id, lat, lon, dong_id) VALUES (?, '싱싱과일', ?, 37.27, 127.11, ?) "
                + "ON DUPLICATE KEY UPDATE shop_name = VALUES(shop_name)", userId(login), categoryId, dongId);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shop.shopName").value("싱싱과일"))
                .andExpect(jsonPath("$.shop.category.name").value("과일·채소 가게"))
                .andExpect(jsonPath("$.shop.dong.name").value("신갈동"))
                .andExpect(jsonPath("$.shop.lat").value(37.27))
                .andExpect(jsonPath("$.shop.lon").value(127.11));
    }

    private long insertDong(String name, String gu) {
        String admCode = UUID.randomUUID().toString().substring(0, 10);
        jdbc.update("INSERT INTO dong (adm_code, name, gu, center_lat, center_lon) VALUES (?, ?, ?, 37.3, 127.1)", admCode, name, gu);
        return jdbc.queryForObject("SELECT id FROM dong WHERE adm_code = ?", Long.class, admCode);
    }

    private String token(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("accessToken").asString();
    }

    private long userId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("user").get("id").asLong();
    }
}
