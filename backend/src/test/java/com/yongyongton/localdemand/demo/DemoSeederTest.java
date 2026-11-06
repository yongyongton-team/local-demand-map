package com.yongyongton.localdemand.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * DEMO_SEED_ENABLED=true 로 서버를 켜서 시연 데이터, 명세 예시 숫자, 발표 1단계(29 → 30), 되돌리기를 확인해요.
 * 테스트마다 끝에 시드를 다시 돌려 시연 계정을 처음 상태로 돌려놔요(테스트 순서와 상관없게).
 */
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
    @DisplayName("예시 요청 수(빵집 6 포함)와 보정동 분포가 명세 예시와 같고, 다시 돌려도 안 늘어나요")
    void seededCounts() {
        assertThat(count(DemoSeeder.GUSEONG, "FRUIT_VEG")).isEqualTo(29);
        assertThat(count(DemoSeeder.GUSEONG, "SIDE_DISH")).isEqualTo(12);
        assertThat(count(DemoSeeder.GUSEONG, "BAKERY")).isEqualTo(6);
        assertThat(count(DemoSeeder.BOJEONG, "FRUIT_VEG")).isEqualTo(14);
        assertThat(count(DemoSeeder.MABUK, "FRUIT_VEG")).isEqualTo(9);

        // 명세 5-5 예시(보정동): 주말 9, 평일 저녁 4, 평일 낮 1 / 늦게까지 8, 낱개 5, 배달 3
        assertThat(like(DemoSeeder.BOJEONG, "time_slots", "WEEKEND")).isEqualTo(9);
        assertThat(like(DemoSeeder.BOJEONG, "time_slots", "WEEKDAY_EVENING")).isEqualTo(4);
        assertThat(like(DemoSeeder.BOJEONG, "time_slots", "WEEKDAY_DAY")).isEqualTo(1);
        assertThat(like(DemoSeeder.BOJEONG, "conditions", "LATE_OPEN")).isEqualTo(8);
        assertThat(like(DemoSeeder.BOJEONG, "conditions", "SMALL_QTY")).isEqualTo(5);
        assertThat(like(DemoSeeder.BOJEONG, "conditions", "DELIVERY")).isEqualTo(3);

        seeder.run(new DefaultApplicationArguments());
        assertThat(count(DemoSeeder.GUSEONG, "FRUIT_VEG")).isEqualTo(29);
        assertThat(count(DemoSeeder.GUSEONG, "SIDE_DISH")).isEqualTo(12);
        Integer demoOwners = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE nickname = '시연 사장님'", Integer.class);
        assertThat(demoOwners).isEqualTo(1);
    }

    @Test
    @DisplayName("시연 사장님 가게는 마북동이고, 3km 칩에 구성동·보정동·마북동이 다 보여요(화면 4)")
    void ownerDemandWithin3km() throws Exception {
        String owner = token("OWNER");
        mockMvc.perform(get("/api/v1/owner/demand").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shop.shopName").value("싱싱과일"))
                .andExpect(jsonPath("$.shop.dong").value("마북동"))
                .andExpect(jsonPath("$.radiusKm").value(3.0))
                .andExpect(jsonPath("$.areas[*].name", hasItems("구성동", "보정동", "마북동")))
                .andExpect(jsonPath("$.areas[0].name").value("구성동"))
                .andExpect(jsonPath("$.areas[0].topTimeSlot").value("WEEKDAY_EVENING"))
                .andExpect(jsonPath("$.areas[?(@.name == '보정동')].topTimeSlot").value("WEEKEND"))
                .andExpect(jsonPath("$.areas[?(@.name == '마북동')].topTimeSlot").value("WEEKDAY_DAY"));
    }

    @Test
    @DisplayName("발표 1단계: 시연 주민이 과일을 요청하면 30명, 근처 시연 사장님 1곳에 알려요. 서버를 다시 켜면(시드 다시) 29로 돌아와요")
    void demoScenarioAndReset() throws Exception {
        JsonNode login = login("RESIDENT");
        String resident = login.get("accessToken").asString();
        long dongId = login.get("user").get("dongId").asLong();
        Long fruitId = jdbc.queryForObject("SELECT id FROM category WHERE code = 'FRUIT_VEG'", Long.class);

        mockMvc.perform(get("/api/v1/me/requests").header("Authorization", "Bearer " + resident))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].category.name").value("반찬가게"))
                .andExpect(jsonPath("$[0].count").value(12));

        mockMvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + resident)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dongId\": " + dongId + ", \"categoryId\": " + fruitId
                                + ", \"conditions\": [\"LATE_OPEN\", \"SMALL_QTY\"]"
                                + ", \"timeSlots\": [\"WEEKDAY_EVENING\", \"WEEKEND\"], \"frequency\": \"WEEKLY_PLUS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.count").value(30))
                .andExpect(jsonPath("$.reachedGoal").value(true))
                .andExpect(jsonPath("$.notifiedOwnerCount").value(1));

        // 발표 2단계처럼 사장님이 제안을 만들고 주민이 선주문
        String owner = token("OWNER");
        OffsetDateTime base = OffsetDateTime.now(ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.SECONDS);
        String offer = mockMvc.perform(post("/api/v1/offers").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dongId\": " + dongId + ", \"categoryId\": " + fruitId
                                + ", \"title\": \"과일 꾸러미\", \"price\": 20000, \"pickupAt\": \"" + base.plusDays(2)
                                + "\", \"pickupPlace\": \"구성동 주민센터 앞\", \"minOrders\": 20, \"deadlineAt\": \""
                                + base.plusDays(1) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long offerId = objectMapper.readTree(offer).get("offer").get("id").asLong();
        mockMvc.perform(post("/api/v1/offers/" + offerId + "/orders").header("Authorization", "Bearer " + resident)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": 1}"))
                .andExpect(status().isCreated());

        // 서버를 다시 켠 것처럼 시드를 다시 돌리면 발표 시작 상태로 돌아와요
        seeder.run(new DefaultApplicationArguments());
        assertThat(count(DemoSeeder.GUSEONG, "FRUIT_VEG")).isEqualTo(29);
        Integer offers = jdbc.queryForObject("""
                SELECT COUNT(*) FROM offer o JOIN app_user u ON o.owner_id = u.id WHERE u.nickname = '시연 사장님'""",
                Integer.class);
        assertThat(offers).isZero();
        mockMvc.perform(get("/api/v1/me/requests").header("Authorization", "Bearer " + resident))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].category.name").value("반찬가게"));
    }

    private JsonNode login(String role) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/demo")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"" + role + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNew").value(false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private String token(String role) throws Exception {
        return login(role).get("accessToken").asString();
    }

    private int count(String admCode, String categoryCode) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM shop_request r JOIN dong d ON r.dong_id = d.id JOIN category c ON r.category_id = c.id
                WHERE d.adm_code = ? AND c.code = ?""", Integer.class, admCode, categoryCode);
    }

    /** 보정동 과일·채소 요청 중 칸(conditions/time_slots)에 코드가 든 건수. */
    private int like(String admCode, String column, String code) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM shop_request r JOIN dong d ON r.dong_id = d.id"
                + " JOIN category c ON r.category_id = c.id WHERE d.adm_code = ? AND c.code = 'FRUIT_VEG'"
                + " AND FIND_IN_SET(?, r." + column + ") > 0", Integer.class, admCode, code);
    }
}
