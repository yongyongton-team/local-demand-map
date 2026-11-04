package com.yongyongton.localdemand;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yongyongton.localdemand.common.config.SecurityConfig;
import com.yongyongton.localdemand.common.error.ApiException;
import com.yongyongton.localdemand.common.error.ErrorCode;
import com.yongyongton.localdemand.common.error.JsonSecurityErrorHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** DB 없이 공통 규칙(주소 앞부분, 로그인 확인, 오류 모양, CORS)을 확인해요. */
@WebMvcTest
@Import({SecurityConfig.class, JsonSecurityErrorHandler.class, ApiBasicsTest.SampleController.class})
class ApiBasicsTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("서버 상태는 로그인 없이 /api/v1/health 로 불러요")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("토큰 없이 다른 API를 부르면 401 UNAUTHORIZED")
    void otherApisNeedLogin() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("ApiException 은 ErrorCode 의 상태 코드와 문장으로 바뀌어요")
    void apiExceptionBecomesErrorBody() throws Exception {
        mockMvc.perform(get("/api/v1/sample/already-requested"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REQUESTED"))
                .andExpect(jsonPath("$.message").value(ErrorCode.ALREADY_REQUESTED.defaultMessage()));
    }

    @Test
    @WithMockUser
    @DisplayName("필수 값이 빠지면 400 VALIDATION_ERROR")
    void invalidBodyIsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/sample/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser
    @DisplayName("JSON이 깨져도 400 VALIDATION_ERROR")
    void brokenJsonIsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/sample/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser
    @DisplayName("없는 주소는 404 NOT_FOUND")
    void unknownPathIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("로컬 프론트 주소에서 오는 요청은 CORS로 허용해요")
    void corsAllowsLocalFrontend() throws Exception {
        mockMvc.perform(options("/api/v1/health")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @RestController
    static class SampleController {

        @GetMapping("/sample/already-requested")
        void alreadyRequested() {
            throw new ApiException(ErrorCode.ALREADY_REQUESTED);
        }

        @PostMapping("/sample/echo")
        String echo(@Valid @RequestBody EchoRequest body) {
            return body.text();
        }
    }

    record EchoRequest(@NotBlank String text) {
    }
}
