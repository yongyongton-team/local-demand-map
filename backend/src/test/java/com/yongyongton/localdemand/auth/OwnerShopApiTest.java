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

    private static final String SQUARE_FEATURE = "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":"
            + "[[[127.10,37.26],[127.12,37.26],[127.12,37.28],[127.10,37.28],[127.10,37.26]]]}}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("사장님이 가게를 등록하면 좌표가 들어가는 동을 찾아 넣고, 다시 부르면 덮어써요")
    void registerAndUpdate() throws Exception {
        long dongId = insertDong("신갈동", SQUARE_FEATURE);
        long categoryId = insertCategory("과일·채소 가게");
        String token = ownerToken();

        mockMvc.perform(put("/api/v1/me/shop").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("싱싱과일", categoryId, 37.27, 127.11)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shop.shopName").value("싱싱과일"))
                .andExpect(jsonPath("$.shop.category.id").value(categoryId))
                .andExpect(jsonPath("$.shop.category.name").value("과일·채소 가게"))
                .andExpect(jsonPath("$.shop.dong.id").value(dongId))
                .andExpect(jsonPath("$.shop.dong.name").value("신갈동"))
                .andExpect(jsonPath("$.shop.lat").value(37.27))
                .andExpect(jsonPath("$.shop.lon").value(127.11));

        // 다시 부르면 덮어써요. 경계 밖 좌표면 동은 null
        mockMvc.perform(put("/api/v1/me/shop").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("싱싱과일 2호점", categoryId, 37.50, 127.50)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shop.shopName").value("싱싱과일 2호점"))
                .andExpect(jsonPath("$.shop.dong").value(nullValue()));

        Long ownerId = jdbc.queryForObject("SELECT id FROM app_user WHERE nickname = '시연 사장님'", Long.class);
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
        String token = ownerToken();
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

    private String ownerToken() throws Exception {
        return token(mockMvc.perform(post("/api/v1/auth/demo")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"OWNER\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
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
        jdbc.update("INSERT INTO dong (adm_code, name, gu, center_lat, center_lon, boundary) VALUES (?, ?, '기흥구', 37.27, 127.11, ?)",
                admCode, name, boundary);
        return jdbc.queryForObject("SELECT id FROM dong WHERE adm_code = ?", Long.class, admCode);
    }

    private long insertCategory(String name) {
        String code = "C" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO category (code, name, sbiz_name, mart_check) VALUES (?, ?, '테스트 업종', TRUE)", code, name);
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
    }
}
