package com.yongyongton.localdemand;

import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 MySQL을 띄워 마이그레이션을 적용하고 서버 전체가 뜨는지 확인해요.
 * 엔티티와 테이블이 안 맞으면(ddl-auto: validate) 여기서 실패해요.
 * Docker가 꺼져 있으면 건너뛰고, GitHub Actions에서는 항상 돌아요.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ApplicationStartTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("마이그레이션으로 테이블을 만들고 서버가 떠요")
    void startsWithMigrations() throws Exception {
        Integer tables = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('dong', 'category', 'app_user', 'shop_request', 'offer', 'pre_order')
                """, Integer.class);
        assertThat(tables).isEqualTo(6);

        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /dongs는 행정동 39개를 반환하고, 항목마다 동 중심 좌표(center)가 있다")
    void dongsReturn39() throws Exception {
        mockMvc.perform(get("/api/v1/dongs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(39))
                .andExpect(jsonPath("$[*].center.lat", hasSize(39)))
                .andExpect(jsonPath("$[*].center.lon", hasSize(39)))
                .andExpect(jsonPath("$[0].center.lat").isNumber());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /categories는 업종 38개를 반환한다")
    void categoriesReturn38() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(38))
                .andExpect(jsonPath("$[0].code").value("FRUIT_VEG"))
                .andExpect(jsonPath("$[0].martCheck").value(true))
                .andExpect(jsonPath("$[0].sbizName").doesNotExist());
    }
    @Test
    @DisplayName("마트 12개와 상가 18,321개가 등록돼요")
    void martAndStoreSeedsHaveExpectedCounts() {
        Integer martCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mart", Integer.class);

        Integer storeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM store", Integer.class);

        Integer mappedStoreCount = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM store s
            JOIN category c ON s.category_id = c.id
            JOIN dong d ON s.dong_id = d.id
            """, Integer.class);

        assertThat(martCount).isEqualTo(12);
        assertThat(storeCount).isEqualTo(18_321);
        assertThat(mappedStoreCount).isEqualTo(18_321);
    }
}
