package com.yongyongton.localdemand.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

    private long userId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("user").get("id").asLong();
    }
}
