package com.yongyongton.localdemand.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/** PUT /me/shop 을 실제 MySQL로 확인해요. Docker가 꺼져 있으면 건너뛰어요. */
@SpringBootTest(properties = "app.demo-login.enabled=true")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OwnerShopApiTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    /**
     * 테스트용 가짜 동 경계. 실제 용인 동 데이터(V3 시드)와 절대 겹치지 않게 한국 밖 좌표(위도 10, 경도 100 근처)를 써요.
     * 경도 100.00~100.02, 위도 10.00~10.02 네모.
     */
    private static final String SQUARE_FEATURE = "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":"
            + "[[[100.00,10.00],[100.02,10.00],[100.02,10.02],[100.00,10.02],[100.00,10.00]]]}}";
    private static final double IN_LAT = 10.01;
    private static final double IN_LON = 100.01;
    private static final double OUT_LAT = 11.0;
    private static final double OUT_LON = 101.0;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TokenService tokenService;

    @Test
    @DisplayName("사장님이 가게를 등록하면 좌표가 들어가는 동을 찾아 넣고, 다시 부르면 덮어써요")
    void registerAndUpdate() throws Exception {
        long dongId = insertDong("테스트동", SQUARE_FEATURE);
        long categoryId = insertCategory("과일·채소 가게");
        long ownerId = newOwner();
        String token = tokenService.issue(ownerId);

        mockMvc.perform(put("/api/v1/me/shop").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("싱싱과일", categoryId, IN_LAT, IN_LON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shop.shopName").value("싱싱과일"))
                .andExpect(jsonPath("$.shop.category.id").value(categoryId))
                .andExpect(jsonPath("$.shop.category.name").value("과일·채소 가게"))
                .andExpect(jsonPath("$.shop.dong.id").value(dongId))
                .andExpect(jsonPath("$.shop.dong.name").value("테스트동"))
                .andExpect(jsonPath("$.shop.lat").value(IN_LAT))
                .andExpect(jsonPath("$.shop.lon").value(IN_LON));

        // 다시 부르면 덮어써요. 경계 밖 좌표면 동은 null
        mockMvc.perform(put("/api/v1/me/shop").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("싱싱과일 2호점", categoryId, OUT_LAT, OUT_LON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shop.shopName").value("싱싱과일 2호점"))
                .andExpect(jsonPath("$.shop.dong").value(nullValue()));

        Integer shops = jdbc.queryForObject("SELECT COUNT(*) FROM owner_shop WHERE user_id = ?", Integer.class, ownerId);
        assertThat(shops).isEqualTo(1);
    }

    @Test
    @DisplayName("사장님이 아니면 403")
    void onlyOwner() throws Exception {
        long categoryId = insertCategory("반찬가게");
        String guestToken = token(mockMvc.perform(post("/api/v1/auth/guest")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        mockMvc.perform(put("/api/v1/me/shop").header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("반찬집", categoryId, 37.27, 127.11)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("없는 업종이면 404, 필수 값이 없으면 400")
    void errors() throws Exception {
        String token = tokenService.issue(newOwner());
        mockMvc.perform(put("/api/v1/me/shop").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("싱싱과일", 999999L, 37.27, 127.11)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(put("/api/v1/me/shop").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\": 1, \"lat\": 37.27, \"lon\": 127.11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /** 이 테스트만의 사장님 계정(시연 사장님은 시드가 켜져 있으면 이미 가게가 있어서 안 써요). */
    private long newOwner() {
        String nickname = "사장" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO app_user (nickname, role) VALUES (?, 'OWNER')", nickname);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE nickname = ?", Long.class, nickname);
    }

    private String token(String json) {
        return objectMapper.readTree(json).get("accessToken").asString();
    }

    private static String body(String shopName, long categoryId, double lat, double lon) {
        return "{\"shopName\": \"" + shopName + "\", \"categoryId\": " + categoryId
                + ", \"lat\": " + lat + ", \"lon\": " + lon + "}";
    }

    private long insertDong(String name, String boundary) {
        String admCode = UUID.randomUUID().toString().substring(0, 10);
        jdbc.update("INSERT INTO dong (adm_code, name, gu, center_lat, center_lon, boundary) VALUES (?, ?, '기흥구', 10.01, 100.01, ?)",
                admCode, name, boundary);
        return jdbc.queryForObject("SELECT id FROM dong WHERE adm_code = ?", Long.class, admCode);
    }

    private long insertCategory(String name) {
        String code = "C" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO category (code, name, sbiz_name, mart_check) VALUES (?, ?, '테스트 업종', TRUE)", code, name);
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
    }
}
