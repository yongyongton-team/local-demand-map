package com.yongyongton.localdemand.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * 입장권(JWT) 도장 키와, 입장권을 만들고(encoder) 검사하는(decoder) 도구.
 * 키는 환경변수 JWT_SECRET(base64, 32바이트 이상)에서 읽어요.
 * 비어 있으면 서버가 켜질 때마다 임시 키를 만들어요. 이때는 서버를 다시 켜면 전에 준 입장권이 무효가 돼요.
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);
    private static final int MIN_KEY_BYTES = 32; // HS256은 256비트(32바이트) 이상 키가 필요해요

    @Bean
    SecretKey jwtSecretKey(@Value("${app.jwt.secret:}") String secret) {
        byte[] bytes;
        if (secret == null || secret.isBlank()) {
            bytes = new byte[MIN_KEY_BYTES];
            new SecureRandom().nextBytes(bytes);
            log.warn("JWT_SECRET이 비어 있어 임시 키를 써요. 서버를 다시 켜면 전에 준 입장권은 무효가 돼요. 배포 서버에는 꼭 JWT_SECRET을 넣어요.");
        } else {
            try {
                bytes = Base64.getDecoder().decode(secret.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("JWT_SECRET은 base64 문자열이어야 해요. 만드는 법은 backend/README.md를 봐요.");
            }
            if (bytes.length < MIN_KEY_BYTES) {
                throw new IllegalStateException("JWT_SECRET이 너무 짧아요. 32바이트 이상 무작위 값을 base64로 넣어요.");
            }
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /**
     * 요청에서 토큰을 꺼내는 규칙. 로그인 없이 쓰는 주소(/auth/**, /health)에서는 토큰을 아예 안 봐요.
     * 그래야 만료된 토큰이 붙어 와도 /auth/guest 로 새 토큰을 받을 수 있어요.
     */
    @Bean
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        return request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (path.startsWith("/api/v1/auth/") || path.equals("/api/v1/health")) {
                return null;
            }
            return resolver.resolve(request);
        };
    }
}
