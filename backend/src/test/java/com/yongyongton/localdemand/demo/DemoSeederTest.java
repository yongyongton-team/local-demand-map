package com.yongyongton.localdemand.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
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

/** DEMO_SEED_ENABLED=true 로 서버를 켜서 시연 데이터와 발표 시나리오 1단계(29 → 30)를 확인해요. */
@SpringBootTest(properties = {"app.demo-seed.enabled=true", "app.demo-login.enabled=true"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DemoSeederTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DemoSeeder seeder;

    @Test
    @DisplayName("예시 요청 수, 시연 사장님 가게(신갈동), 다시 돌려도 안 늘어나요")
    void seededCounts() {
        // 과일은 예시 주민 것만 세요(발표 1단계 테스트가 시연 주민 과일 요청을 더할 수 있어서)
        assertThat(fakeCount(DemoSeeder.GUSEONG, "FRUIT_VEG")).isEqualTo(29);
        assertThat(count(DemoSeeder.GUSEONG, "SIDE_DISH")).isEqualTo(12);
        assertThat(count(DemoSeeder.BOJEONG, "FRUIT_VEG")).isEqualTo(14);
        assertThat(count(DemoSeeder.MABUK, "FRUIT_VEG")).isEqualTo(9);

        String shopDong = jdbc.queryForObject("""
                SELECT d.name FROM owner_shop s JOIN app_user u ON s.user_id = u.id JOIN dong d ON s.dong_id = d.id
                WHERE u.nickname = '시연 사장님'""", String.class);
        assertThat(shopDong).isEqualTo("신갈동");

        seeder.run(new DefaultApplicationArguments());
        assertThat(fakeCount(DemoSeeder.GUSEONG, "FRUIT_VEG")).isEqualTo(29);
        assertThat(count(DemoSeeder.GUSEONG, "SIDE_DISH")).isEqualTo(12);
        Integer demoOwners = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE nickname = '시연 사장님'", Integer.class);
        assertThat(demoOwners).isEqualTo(1);
    }

    @Test
    @DisplayName("발표 1단계: 시연 주민(구성동)이 과일을 요청하면 30명이 차고, 근처 시연 사장님에게 알려져요")
    void demoScenario() throws Exception {
        String login = mockMvc.perform(post("/api/v1/auth/demo")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"RESIDENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNew").value(false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = objectMapper.readTree(login).get("accessToken").asString();
        long dongId = objectMapper.readTree(login).get("user").get("dongId").asLong();
        Long fruitId = jdbc.queryForObject("SELECT id FROM category WHERE code = 'FRUIT_VEG'", Long.class);

        mockMvc.perform(get("/api/v1/me/requests").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].category.name").value("반찬가게"))
                .andExpect(jsonPath("$[0].count").value(12));

        mockMvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dongId\": " + dongId + ", \"categoryId\": " + fruitId
                                + ", \"timeSlots\": [\"WEEKDAY_EVENING\", \"WEEKEND\"], \"frequency\": \"WEEKLY_PLUS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.count").value(30))
                .andExpect(jsonPath("$.reachedGoal").value(true))
                .andExpect(jsonPath("$.notifiedOwnerCount").value(1));
    }

    private int fakeCount(String admCode, String categoryCode) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM shop_request r JOIN dong d ON r.dong_id = d.id JOIN category c ON r.category_id = c.id
                JOIN app_user u ON r.user_id = u.id
                WHERE d.adm_code = ? AND c.code = ? AND u.nickname LIKE '예시주민%'""", Integer.class, admCode, categoryCode);
    }

    private int count(String admCode, String categoryCode) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM shop_request r JOIN dong d ON r.dong_id = d.id JOIN category c ON r.category_id = c.id
                WHERE d.adm_code = ? AND c.code = ?""", Integer.class, admCode, categoryCode);
    }
}
