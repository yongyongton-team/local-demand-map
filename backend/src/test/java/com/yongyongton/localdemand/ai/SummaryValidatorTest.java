package com.yongyongton.localdemand.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SummaryValidatorTest {

    private static Map<String, Object> facts() {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("동네", "영덕1동");
        facts.put("요청한 주민 수", 14L);
        facts.put("비율", 0.64);
        facts.put("거리", 2.4);
        facts.put("갈 수 있는 때", List.of(Map.of("때", "주말", "요청 수", 9L)));
        return facts;
    }

    @Test
    @DisplayName("자료에 있는 숫자, 이름 속 숫자, 비율의 퍼센트는 통과")
    void allowedNumbers() {
        assertThat(SummaryValidator.isValid("영덕1동에는 주민 14명이 원해요. 주말에 9명이 갈 수 있어요.", facts())).isTrue();
        assertThat(SummaryValidator.isValid("가장 가까운 곳은 2.4km예요. 64%가 주말을 골랐어요.", facts())).isTrue();
        assertThat(SummaryValidator.isValid("숫자 없이도 괜찮아요.", facts())).isTrue();
    }

    @Test
    @DisplayName("자료에 없는 숫자가 하나라도 있으면 탈락")
    void unknownNumber() {
        assertThat(SummaryValidator.isValid("주민 15명이 원해요.", facts())).isFalse();
        assertThat(SummaryValidator.isValid("주민 14명, 주말 23명이에요.", facts())).isFalse();
        assertThat(SummaryValidator.isValid("약 2km 떨어져 있어요.", facts())).isFalse();
        // 이름 속 숫자(영덕1동의 1)를 따로 쓰면 탈락
        assertThat(SummaryValidator.isValid("영덕1동에는 주민 1명이 원해요.", facts())).isFalse();
    }

    @Test
    @DisplayName("단정 표현, 빈 문장, 너무 긴 문장은 탈락")
    void bannedAndLength() {
        assertThat(SummaryValidator.isValid("주민 14명이 원해서 반드시 잘될 거예요.", facts())).isFalse();
        assertThat(SummaryValidator.isValid("확실히 좋아요.", facts())).isFalse();
        assertThat(SummaryValidator.isValid(" ", facts())).isFalse();
        assertThat(SummaryValidator.isValid(null, facts())).isFalse();
        assertThat(SummaryValidator.isValid("가".repeat(SummaryValidator.MAX_LENGTH + 1), facts())).isFalse();
    }

    @Test
    @DisplayName("숫자 모양 맞추기: 쉼표·끝자리 0을 지워요")
    void normalize() {
        assertThat(SummaryValidator.normalize("1,234")).isEqualTo("1234");
        assertThat(SummaryValidator.normalize("30.0")).isEqualTo("30");
        assertThat(SummaryValidator.normalize("2.40")).isEqualTo("2.4");
        assertThat(SummaryValidator.normalize("100")).isEqualTo("100");
    }
}
