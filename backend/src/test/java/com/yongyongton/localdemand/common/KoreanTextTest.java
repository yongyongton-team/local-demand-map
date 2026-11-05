package com.yongyongton.localdemand.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KoreanTextTest {

    @Test
    @DisplayName("받침이 있으면 을, 없으면 를")
    void josa() {
        assertThat(KoreanText.josa("빵집", "을", "를")).isEqualTo("빵집을");
        assertThat(KoreanText.josa("약국", "을", "를")).isEqualTo("약국을");
        assertThat(KoreanText.josa("과일·채소 가게", "을", "를")).isEqualTo("과일·채소 가게를");
        assertThat(KoreanText.josa("CU", "을", "를")).isEqualTo("CU를");
    }

    @Test
    @DisplayName("하버사인 거리: 위도 0.02도 ≈ 2.2km, 소수 첫째 자리까지")
    void distance() {
        assertThat(Geo.round1(Geo.distanceKm(37.30, 127.10, 37.32, 127.10))).isEqualTo(2.2);
    }
}
