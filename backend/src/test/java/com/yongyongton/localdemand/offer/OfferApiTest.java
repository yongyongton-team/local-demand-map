package com.yongyongton.localdemand.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yongyongton.localdemand.auth.TokenService;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/** 선주문 수령 API를 실제 MySQL로 확인해요. Docker가 꺼져 있으면 건너뛰어요. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OfferApiTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TokenService tokenService;

    @Test
    @DisplayName("제안 만들기: 201, 명세 모양, notifiedResidentCount = 그 동·업종 요청 수")
    void create() throws Exception {
        Fixture f = fixture();
        insertRequest(f.dongId, f.categoryId);
        insertRequest(f.dongId, f.categoryId);

        offer(f, 20)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.offer.id").isNumber())
                .andExpect(jsonPath("$.offer.status").value("OPEN"))
                .andExpect(jsonPath("$.offer.orderCount").value(0))
                .andExpect(jsonPath("$.offer.minOrders").value(20))
                .andExpect(jsonPath("$.notifiedResidentCount").value(2));
    }

    @Test
    @DisplayName("제안 만들기 규칙: 사장님 아니면 403, 다른 업종 400, 마감이 수령보다 늦으면 400, minOrders 0이면 400")
    void createRules() throws Exception {
        Fixture f = fixture();
        String resident = resident(f.dongId);
        mockMvc.perform(post("/api/v1/offers").header("Authorization", "Bearer " + resident)
                        .contentType(MediaType.APPLICATION_JSON).content(offerBody(f.dongId, f.categoryId, 20, 1, 2)))
                .andExpect(status().isForbidden());

        long otherCategory = insertCategory("다른 업종");
        mockMvc.perform(post("/api/v1/offers").header("Authorization", "Bearer " + f.ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(offerBody(f.dongId, otherCategory, 20, 1, 2)))
                .andExpect(status().isBadRequest());

        // 마감(2일 뒤)이 수령(1일 뒤)보다 늦음
        mockMvc.perform(post("/api/v1/offers").header("Authorization", "Bearer " + f.ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(offerBody(f.dongId, f.categoryId, 20, 2, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/offers").header("Authorization", "Bearer " + f.ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(offerBody(f.dongId, f.categoryId, 0, 1, 2)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("선주문: 수량 합계로 세고, 두 번이면 409, 취소 후 다시 주문 가능, 화면 3에 myOrder로 보여요")
    void orderFlow() throws Exception {
        Fixture f = fixture();
        long offerId = offerId(offer(f, 20));
        String resident = resident(f.dongId);
        // 화면 3에 보이려면 그 동·업종을 요청한 주민이어야 해요
        mockMvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + resident)
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody(f.dongId, f.categoryId)))
                .andExpect(status().isCreated());

        MvcResult ordered = order(resident, offerId, 3)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.offerId").value(offerId))
                .andExpect(jsonPath("$.order.quantity").value(3))
                .andExpect(jsonPath("$.order.status").value("RESERVED"))
                .andExpect(jsonPath("$.orderCount").value(3))
                .andExpect(jsonPath("$.minOrders").value(20))
                .andReturn();
        long orderId = json(ordered).get("order").get("id").asLong();

        order(resident, offerId, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_ORDERED"));
        order(resident, offerId, 11).andExpect(status().isBadRequest());
        order(resident(insertDong("다른동")), offerId, 1).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/me/requests").header("Authorization", "Bearer " + resident))
                .andExpect(jsonPath("$[0].offers", hasSize(1)))
                .andExpect(jsonPath("$[0].offers[0].id").value(offerId))
                .andExpect(jsonPath("$[0].offers[0].shopName").value("싱싱과일"))
                .andExpect(jsonPath("$[0].offers[0].title").value("과일 꾸러미"))
                .andExpect(jsonPath("$[0].offers[0].price").value(20000))
                .andExpect(jsonPath("$[0].offers[0].pickupPlace").value("구성동 주민센터 앞"))
                .andExpect(jsonPath("$[0].offers[0].orderCount").value(3))
                .andExpect(jsonPath("$[0].offers[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].offers[0].myOrder.id").value(orderId))
                .andExpect(jsonPath("$[0].offers[0].myOrder.quantity").value(3));

        mockMvc.perform(delete("/api/v1/orders/" + orderId).header("Authorization", "Bearer " + resident))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/me/requests").header("Authorization", "Bearer " + resident))
                .andExpect(jsonPath("$[0].offers[0].orderCount").value(0))
                .andExpect(jsonPath("$[0].offers[0].myOrder").value(nullValue()));

        order(resident, offerId, 2).andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.id").value(orderId))
                .andExpect(jsonPath("$.orderCount").value(2));
    }

    @Test
    @DisplayName("사장님 내 제안 목록과 취소: 취소하면 CANCELLED, 또 취소하면 409, 취소된 제안엔 주문 409, 화면 3에서 빠져요")
    void ownerListAndCancel() throws Exception {
        Fixture f = fixture();
        long offerId = offerId(offer(f, 20));
        String resident = resident(f.dongId);
        mockMvc.perform(post("/api/v1/requests").header("Authorization", "Bearer " + resident)
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody(f.dongId, f.categoryId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/owner/offers").header("Authorization", "Bearer " + f.ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(offerId))
                .andExpect(jsonPath("$[0].dong.name").value("구성동"))
                .andExpect(jsonPath("$[0].title").value("과일 꾸러미"))
                .andExpect(jsonPath("$[0].orderCount").value(0))
                .andExpect(jsonPath("$[0].status").value("OPEN"));

        mockMvc.perform(post("/api/v1/offers/" + offerId + "/cancel").header("Authorization", "Bearer " + f.ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/api/v1/offers/" + offerId + "/cancel").header("Authorization", "Bearer " + f.ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_CLOSED"));
        order(resident, offerId, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_CLOSED"));
        mockMvc.perform(get("/api/v1/me/requests").header("Authorization", "Bearer " + resident))
                .andExpect(jsonPath("$[0].offers", hasSize(0)));

        // 남의 제안은 취소 못 해요
        Fixture other = fixture();
        mockMvc.perform(post("/api/v1/offers/" + offerId + "/cancel").header("Authorization", "Bearer " + other.ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("주문이 있는 제안을 사장님이 취소하면 선주문도 같이 CANCELLED, orderCount 0, 주민 취소는 그냥 204")
    void cancelOfferWithOrders() throws Exception {
        Fixture f = fixture();
        long offerId = offerId(offer(f, 20));
        String resident = resident(f.dongId);
        long orderId = json(order(resident, offerId, 3).andExpect(status().isCreated()).andReturn())
                .get("order").get("id").asLong();
        order(resident(f.dongId), offerId, 2).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/offers/" + offerId + "/cancel").header("Authorization", "Bearer " + f.ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.orderCount").value(0));

        Integer reserved = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pre_order WHERE offer_id = ? AND status = 'RESERVED'", Integer.class, offerId);
        Integer cancelled = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pre_order WHERE offer_id = ? AND status = 'CANCELLED'", Integer.class, offerId);
        assertThat(reserved).isZero();
        assertThat(cancelled).isEqualTo(2);

        // 주민이 뒤늦게 취소를 눌러도 막히지 않아요(이미 취소돼 있어서 그냥 204)
        mockMvc.perform(delete("/api/v1/orders/" + orderId).header("Authorization", "Bearer " + resident))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("마감이 지나면: 채웠으면 CONFIRMED → 수령 끝 가능(선주문은 PICKED_UP), 못 채웠으면 CLOSED → 수령 끝 409, 주문·취소도 409")
    void deadline() throws Exception {
        Fixture f = fixture();
        long filled = offerId(offer(f, 2));
        long notFilled = offerId(offer(f, 5));
        String resident = resident(f.dongId);
        long orderId = json(order(resident, filled, 2).andExpect(status().isCreated()).andReturn())
                .get("order").get("id").asLong();
        order(resident, notFilled, 1).andExpect(status().isCreated());

        jdbc.update("UPDATE offer SET deadline_at = ? WHERE id IN (?, ?)",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now(KST).minusHours(1)), filled, notFilled);

        mockMvc.perform(get("/api/v1/owner/offers").header("Authorization", "Bearer " + f.ownerToken))
                .andExpect(jsonPath("$[?(@.id == " + filled + ")].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[?(@.id == " + notFilled + ")].status").value("CLOSED"));

        order(resident(f.dongId), filled, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_CLOSED"));
        mockMvc.perform(delete("/api/v1/orders/" + orderId).header("Authorization", "Bearer " + resident))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/offers/" + notFilled + "/done").header("Authorization", "Bearer " + f.ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_CLOSED"));
        mockMvc.perform(post("/api/v1/offers/" + filled + "/done").header("Authorization", "Bearer " + f.ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.orderCount").value(2));
        String orderStatus = jdbc.queryForObject("SELECT status FROM pre_order WHERE id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("PICKED_UP");
    }

    // ───────────── 준비 도우미 ─────────────

    /** 동·업종과, 그 업종 가게를 등록한 사장님. */
    private record Fixture(long dongId, long categoryId, String ownerToken) {
    }

    private Fixture fixture() {
        long dongId = insertDong("구성동");
        long categoryId = insertCategory("과일·채소 가게");
        long ownerId = insertUser("OWNER", null);
        jdbc.update("INSERT INTO owner_shop (user_id, shop_name, category_id, lat, lon, dong_id) VALUES (?, '싱싱과일', ?, 37.27, 127.11, ?)",
                ownerId, categoryId, dongId);
        return new Fixture(dongId, categoryId, tokenService.issue(ownerId));
    }

    private String resident(long dongId) {
        return tokenService.issue(insertUser("RESIDENT", dongId));
    }

    private long insertUser(String role, Long dongId) {
        String nickname = "테스트" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO app_user (nickname, role, dong_id) VALUES (?, ?, ?)", nickname, role, dongId);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE nickname = ?", Long.class, nickname);
    }

    private long insertDong(String name) {
        String admCode = UUID.randomUUID().toString().substring(0, 10);
        jdbc.update("INSERT INTO dong (adm_code, name, gu, center_lat, center_lon) VALUES (?, ?, '기흥구', 37.3, 127.11)",
                admCode, name);
        return jdbc.queryForObject("SELECT id FROM dong WHERE adm_code = ?", Long.class, admCode);
    }

    private long insertCategory(String name) {
        String code = "C" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO category (code, name, sbiz_name, mart_check) VALUES (?, ?, '테스트 업종', TRUE)", code, name);
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
    }

    private void insertRequest(long dongId, long categoryId) {
        long userId = insertUser("RESIDENT", dongId);
        jdbc.update("INSERT INTO shop_request (user_id, dong_id, category_id, time_slots, frequency) VALUES (?, ?, ?, 'WEEKEND', 'WEEKLY_PLUS')",
                userId, dongId, categoryId);
    }

    private ResultActions offer(Fixture f, int minOrders) throws Exception {
        return mockMvc.perform(post("/api/v1/offers").header("Authorization", "Bearer " + f.ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(offerBody(f.dongId, f.categoryId, minOrders, 1, 2)));
    }

    private ResultActions order(String token, long offerId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/v1/offers/" + offerId + "/orders").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": " + quantity + "}"));
    }

    /** 마감은 deadlineDays일 뒤, 수령은 pickupDays일 뒤. */
    private static String offerBody(long dongId, long categoryId, int minOrders, int deadlineDays, int pickupDays) {
        OffsetDateTime base = OffsetDateTime.now(KST).truncatedTo(ChronoUnit.SECONDS);
        return "{\"dongId\": " + dongId + ", \"categoryId\": " + categoryId
                + ", \"title\": \"과일 꾸러미\", \"description\": \"제철 과일 4~5가지, 낱개 포장\", \"price\": 20000"
                + ", \"pickupAt\": \"" + base.plusDays(pickupDays) + "\", \"pickupPlace\": \"구성동 주민센터 앞\""
                + ", \"minOrders\": " + minOrders + ", \"deadlineAt\": \"" + base.plusDays(deadlineDays) + "\"}";
    }

    private static String requestBody(long dongId, long categoryId) {
        return "{\"dongId\": " + dongId + ", \"categoryId\": " + categoryId
                + ", \"timeSlots\": [\"WEEKEND\"], \"frequency\": \"WEEKLY_PLUS\"}";
    }

    private long offerId(ResultActions result) throws Exception {
        return json(result.andExpect(status().isCreated()).andReturn()).get("offer").get("id").asLong();
    }

    private tools.jackson.databind.JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
